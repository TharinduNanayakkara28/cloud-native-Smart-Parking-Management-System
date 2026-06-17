# Phase 2 — Sensor Simulator + Spot Detection Service

## Goal
Stand up the data source layer. A software simulator replaces physical sensors, publishing spot state change events to Kafka. The Spot Detection Service consumes those events, persists authoritative spot state to PostgreSQL+PostGIS, and re-publishes normalised events downstream.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| sensor-simulator | 8082 | In-memory spot registry, auto/manual state flipping, Kafka producer |
| spot-detection-service | 8083 | Kafka consumer, authoritative spot state DB, Kafka re-publisher |
| postgres-spot-detection | 5433 | Dedicated PostGIS DB for spot-detection-service |
| kafka | 9092 | Event bus (KRaft mode, no Zookeeper) |

## Event Flow

```
Sensor Simulator
  │  publishes spot.occupied / spot.freed
  ▼
Kafka topic: spot-events
  │
  ▼
Spot Detection Service (consumer)
  ├── updates spots table
  ├── appends to spot_events log
  └── publishes spot.state.changed
        │
        ▼
      Kafka topic: spot-state
        │
        └──► Availability Service (Phase 3)
        └──► Analytics Service  (Phase 8)
```

## Spot Layout (18 spots — 3 rows × 6 spots)

```
Parking Lot: 00000000-0000-0000-0000-000000000100

Row A (lat 6.92710):  A1  A2  A3  A4  A5  A6
Row B (lat 6.92721):  B1  B2  B3  B4  B5  B6
Row C (lat 6.92732):  C1  C2  C3  C4  C5  C6
```

Each spot is separated ~3m longitudinally (0.000030°) and ~12m between rows.
Spot UUIDs are fixed (`00000000-0000-0000-0000-00000000000N` where N = 1..18) to match the Flyway seed data.

## Kafka Topics

| Topic | Key | Value | Producer → Consumer |
|---|---|---|---|
| `spot-events` | spotId | `SpotSensorEvent` JSON | Simulator → SpotDetection |
| `spot-state` | spotId | `SpotStateChangedEvent` JSON | SpotDetection → (Availability, Analytics) |

## Running Phase 2 Locally

```bash
docker-compose -f infra/docker-compose.yml up --build
```

Start auto simulation:
```bash
curl -X POST http://localhost:8080/simulate/auto/start
```

Manually flip a spot:
```bash
curl -X POST http://localhost:8080/simulate/spot/00000000-0000-0000-0000-000000000001/occupy
```

Query all spots (admin):
```bash
curl http://localhost:8080/spots \
  -H "Authorization: Bearer <accessToken>"
```

## Environment Variables

| Variable | Default | Service |
|---|---|---|
| `KAFKA_BROKER` | `localhost:9092` | simulator, spot-detection |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5433/spotdetectiondb` | spot-detection |
| `SIMULATOR_SERVICE_URL` | `http://localhost:8082` | api-gateway |
| `SPOT_DETECTION_SERVICE_URL` | `http://localhost:8083` | api-gateway |
