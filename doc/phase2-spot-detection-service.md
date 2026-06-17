# Spot Detection Service — Implementation Details

## Responsibility
Single source of truth for raw spot state. Consumes sensor events, persists every state transition, and re-publishes a richer normalised event that includes location data for downstream services.

## Package Structure

```
com.smartparking.spotdetection
├── config/
│   └── KafkaConfig.java              # Topic beans + consumer/producer config
├── controller/
│   └── SpotAdminController.java      # GET /spots, GET /spots/{id}
├── dto/
│   ├── SpotSensorEvent.java          # Inbound Kafka message (from simulator)
│   ├── SpotSensorPayload.java
│   ├── SpotStateChangedEvent.java    # Outbound Kafka message
│   ├── SpotStateChangedPayload.java
│   └── SpotResponse.java             # REST response DTO
├── kafka/
│   ├── SpotEventConsumer.java        # @KafkaListener on spot-events
│   └── SpotStatePublisher.java       # KafkaTemplate → spot-state topic
├── model/
│   ├── Spot.java                     # JPA entity (spots table)
│   ├── SpotEventRecord.java          # JPA entity (spot_events log table)
│   └── SpotState.java                # Enum: FREE, OCCUPIED, RESERVED
├── repository/
│   ├── SpotRepository.java
│   └── SpotEventRecordRepository.java
└── service/
    └── SpotDetectionService.java     # Core processing logic
```

## Database Schema (Flyway V1)

```sql
spots       -- authoritative spot state + PostGIS location
spot_events -- immutable append-only event log
```

The `location GEOGRAPHY(POINT, 4326)` column is populated from `latitude`/`longitude` at insert time. Phase 3 (Availability Service) will use `ST_DWithin` queries on this column via REST to the Spot Detection Service.

JPA only maps `latitude` and `longitude` as Java doubles — the PostGIS column is DB-managed and not mapped to a Java field (no Hibernate Spatial dependency needed in Phase 2).

## Consumer Processing Flow

```
@KafkaListener(topic="spot-events")
    │
    ├── Deserialize String → SpotSensorEvent (manual Jackson parse)
    │
    ├── Look up Spot by spotId in DB
    │   └── Not found? → log warning, skip
    │
    ├── Capture previousState
    ├── Update spot.state + spot.last_updated
    ├── Append SpotEventRecord (immutable log entry)
    │
    └── Publish SpotStateChangedEvent to spot-state topic
         (includes latitude, longitude for downstream services)
```

## Outbound Event Schema

**Topic:** `spot-state`  
**Key:** spotId (String)

```json
{
  "eventType": "spot.state.changed",
  "timestamp": "2024-01-01T10:00:00Z",
  "payload": {
    "spotId": "00000000-0000-0000-0000-000000000001",
    "spotNumber": "A1",
    "lotId": "00000000-0000-0000-0000-000000000100",
    "previousState": "FREE",
    "newState": "OCCUPIED",
    "latitude": 6.92710,
    "longitude": 79.86100,
    "source": "simulator"
  }
}
```

## Admin REST Endpoints (JWT protected via gateway)

```
GET /spots          List all spots with current state
GET /spots/{id}     Get single spot detail
```

## Error Handling

- Unknown spotId in Kafka event → logged as WARN, message acknowledged (not retried)
- DB errors → propagated as exception, Kafka will retry based on consumer `retry` config
- Duplicate events → idempotent update (same state → same state, still appends to event log)
