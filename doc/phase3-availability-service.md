# Availability Service — Implementation Details

## Design Decisions

### Redis is the source of truth for state
Spot location data (static) lives in PostGIS. Spot state (dynamic) lives entirely in Redis, updated on every Kafka event. The DB is queried only for the spatial filter — never for state.

### Two-tier read path
```
GET /spots/available
    │
    ├── 1. PostGIS ST_DWithin → candidate spot IDs within radius
    └── 2. Redis MGET spot:{id}:state → filter to FREE only
              └── burst protection: availability:full-map (5s TTL)
                  used by GET /spots/state (all-spots, no geo filter)
```

### WebSocket via STOMP
Clients connect to `/ws/availability` (SockJS endpoint), subscribe to `/topic/spots`. On every Kafka event the service broadcasts a `SpotStateUpdate` message. No JWT on the WS connect — auth is intentionally deferred (suitable for public parking dashboards; can be added in Phase 9).

## Package Structure

```
com.smartparking.availability
├── config/
│   ├── KafkaConfig.java           # Consumer group + spot-state topic bean
│   ├── RedisConfig.java           # StringRedisTemplate + Jackson mapper
│   └── WebSocketConfig.java       # STOMP broker + /ws/availability endpoint
├── controller/
│   └── AvailabilityController.java # GET /spots/available, GET /spots/state
├── dto/
│   ├── SpotStateChangedEvent.java  # Inbound Kafka (from spot-detection)
│   ├── SpotStateChangedPayload.java
│   ├── AvailableSpotResponse.java  # REST response
│   └── SpotStateUpdate.java        # WebSocket broadcast payload
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   └── SpotStateConsumer.java      # @KafkaListener on spot-state
├── model/
│   └── AvailabilitySpot.java       # JPA entity (location-only, no state)
├── repository/
│   └── AvailabilitySpotRepository.java # native ST_DWithin query
└── service/
    └── AvailabilityService.java    # Redis + WS + geo-query orchestration
```

## Database Schema (Flyway V1)

```sql
availability_spots  -- static location index (same 18 UUIDs as spot-detection)
```

No state column — state lives exclusively in Redis.
The `location GEOGRAPHY(POINT, 4326)` column is used by `ST_DWithin` and indexed with GIST.

The native query selects only non-geometry columns (`id, lot_id, spot_number, floor, latitude, longitude`) so JPA does not need Hibernate Spatial.

## PostGIS Query

```sql
SELECT id, lot_id, spot_number, floor, latitude, longitude
FROM availability_spots
WHERE ST_DWithin(
    location,
    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
    :radiusMeters
)
```

Note: `ST_MakePoint(longitude, latitude)` — PostGIS uses (x=lng, y=lat) order.

## Redis Operations

```
# Kafka consumer path
SET  spot:{spotId}:state   FREE          (no expiry)
DEL  availability:full-map               (evict burst cache)

# Query path
MGET spot:{id1}:state  spot:{id2}:state  …   (batch read)

# Burst cache path  (GET /spots/state)
GET  availability:full-map               → deserialize if present
SET  availability:full-map  <json>  EX 5 → rebuild + cache 5s if miss
```

## WebSocket Broadcast Payload

```json
{
  "spotId":      "00000000-0000-0000-0000-000000000001",
  "spotNumber":  "A1",
  "lotId":       "00000000-0000-0000-0000-000000000100",
  "previousState": "FREE",
  "newState":    "OCCUPIED",
  "latitude":    6.9271,
  "longitude":   79.861,
  "timestamp":   "2024-01-01T10:00:00Z"
}
```

## Kafka Consumer Error Handling

- Parse failure → log ERROR, acknowledge (don't retry bad messages)
- Redis failure → log ERROR, still try to broadcast WS (partial degradation)
- Unknown spotId → log WARN, skip
