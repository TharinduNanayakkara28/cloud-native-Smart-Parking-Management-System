# Sensor Simulator — Implementation Details

## Design Principle
The simulator is a drop-in replacement for a physical sensor gateway. It publishes the exact same Kafka events a real sensor would. Every downstream service is completely decoupled from whether the source is hardware or this simulator.

## Package Structure

```
com.smartparking.simulator
├── config/
│   └── KafkaConfig.java          # NewTopic beans for spot-events
├── controller/
│   └── SimulatorController.java  # REST admin endpoints
├── dto/
│   ├── SpotSensorEvent.java      # Kafka message wrapper
│   ├── SpotSensorPayload.java    # Event payload
│   └── SpotResponse.java         # REST response DTO
├── kafka/
│   └── SpotEventPublisher.java   # KafkaTemplate wrapper
├── model/
│   ├── ParkingSpot.java          # In-memory spot model
│   └── SpotState.java            # Enum: FREE, OCCUPIED
└── service/
    ├── SpotRegistry.java         # ConcurrentHashMap of 18 spots, seeded on startup
    └── SimulatorService.java     # Auto-sim @Scheduled job + manual flip methods
```

## In-Memory Spot Registry

18 spots are seeded at startup via `@PostConstruct`. Spot IDs and positions are **fixed** — they match the UUIDs and coordinates seeded in the Spot Detection DB Flyway migration.

| Spot | UUID (last segment) | Lat | Lng |
|---|---|---|---|
| A1 | ...001 | 6.92710 | 79.86100 |
| A2 | ...002 | 6.92710 | 79.86130 |
| ... | | | |
| B1 | ...007 | 6.92721 | 79.86100 |
| ... | | | |
| C6 | ...018 | 6.92732 | 79.86250 |

## Auto Simulation

`SimulatorService` has a `@Scheduled(fixedDelay = 2000)` method that runs every 2 seconds.

When active:
1. Randomly picks a spot from the registry
2. Flips its state (`FREE → OCCUPIED` or `OCCUPIED → FREE`)
3. Delegates to `SpotEventPublisher` to send the Kafka event

A `volatile boolean autoRunning` flag gates execution — `POST /simulate/auto/start` and `/stop` toggle it.

## Kafka Event Schema

**Topic:** `spot-events`  
**Key:** spotId (String)

```json
{
  "eventType": "spot.occupied",
  "timestamp": "2024-01-01T10:00:00Z",
  "payload": {
    "spotId": "00000000-0000-0000-0000-000000000001",
    "spotNumber": "A1",
    "lotId": "00000000-0000-0000-0000-000000000100",
    "state": "OCCUPIED",
    "source": "simulator"
  }
}
```

`eventType` is `"spot.occupied"` when state becomes OCCUPIED, `"spot.freed"` when state becomes FREE.

## REST Endpoints

```
POST /simulate/auto/start          Start automatic random flipping (every 2s)
POST /simulate/auto/stop           Stop automatic flipping
GET  /simulate/status              {"autoRunning": true/false, "spotCount": 18}
GET  /simulate/spots               List all 18 spots and their current in-memory state
POST /simulate/spot/{id}/occupy    Manually set a spot to OCCUPIED
POST /simulate/spot/{id}/free      Manually set a spot to FREE
```

These endpoints are exposed through the API Gateway at the same paths (no JWT required — admin/demo use).
