# Phase 4 — Reservation Service

## Goal
Implement the core feature: a driver can reserve a free spot, arrive to check in, and check out at the end. Distributed locking prevents two drivers from booking the same spot simultaneously. A scheduled job expires un-fulfilled reservations. The saga pattern wires reservation lifecycle to the Payment Service (Phase 5).

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| reservation-service | 8085 | Reservation CRUD, Redis distributed lock, expiry scheduler, Kafka producer/consumer |
| postgres-reservation | 5435 | Dedicated DB for reservation-service |
| redis | 6379 | Distributed lock (`lock:spot:{spotId}`) — already running |

## Architecture

```
POST /reservations (via gateway)
  │
  ▼
Reservation Service
  ├── 1. GET http://availability-service:8084/spots/{spotId}/state
  │       └── state != FREE → 409 Spot not available
  ├── 2. SETNX lock:spot:{spotId}  EX 10
  │       └── lock held → 409 Spot being reserved
  ├── 3. INSERT INTO reservations (status=PENDING)
  ├── 4. DEL lock:spot:{spotId}  (atomic compare-and-delete via Lua)
  └── 5. Kafka → reservation-events  (reservation.created)


Kafka: payment-events
  ├── payment.success → reservation status = ACTIVE, publish reservation.active
  └── payment.failed  → reservation status = CANCELLED, publish reservation.cancelled

@Scheduled every 60s
  └── PENDING reservations past reserved_until → EXPIRED, publish reservation.expired
```

## Reservation Status Machine

```
         ┌──────────────────────────────┐
         │                              │
PENDING ──► ACTIVE ──► COMPLETED        │
   │         │                          │
   │         └──► CANCELLED             │
   │                                    │
   └──────────────────────► EXPIRED ────┘
```

| Transition | Trigger |
|---|---|
| PENDING → ACTIVE | `payment.success` Kafka event |
| PENDING → EXPIRED | Scheduler (reserved_until < now) |
| PENDING → CANCELLED | `payment.failed` or driver cancels |
| ACTIVE → ACTIVE (checked in) | `POST /reservations/:id/checkin` |
| ACTIVE → COMPLETED | `POST /reservations/:id/checkout` |
| ACTIVE → CANCELLED | `POST /reservations/:id/cancel` |

## Distributed Lock Design

```
Key:   lock:spot:{spotId}
Value: {reservationId}  (UUID of the in-flight reservation attempt)
TTL:   10 seconds       (auto-expires if service crashes before release)

Acquire:  SETNX → returns true only for the first caller
Release:  Lua script (atomic compare-and-delete to avoid releasing another owner's lock)
  if redis.call('get', KEYS[1]) == ARGV[1]
    then return redis.call('del', KEYS[1])
    else return 0
  end
```

## Kafka Events Published

| Topic | Event | When |
|---|---|---|
| `reservation-events` | `reservation.created` | Reservation saved (PENDING) |
| `reservation-events` | `reservation.active` | `payment.success` consumed |
| `reservation-events` | `reservation.cancelled` | Cancelled by driver or `payment.failed` |
| `reservation-events` | `reservation.expired` | Scheduler detects overdue PENDING |
| `reservation-events` | `reservation.completed` | Driver checks out |

## Kafka Events Consumed

| Topic | Event | Action |
|---|---|---|
| `payment-events` | `payment.success` | Set reservation ACTIVE, publish `reservation.active` |
| `payment-events` | `payment.failed` | Set reservation CANCELLED, publish `reservation.cancelled` |

## Running Phase 4

```bash
docker-compose -f infra/docker-compose.yml up --build
```

### Create a reservation
```bash
TOKEN="<accessToken from POST /auth/login>"

curl -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "spotId": "00000000-0000-0000-0000-000000000001",
    "vehiclePlate": "ABC-1234",
    "reservedFrom": "2026-06-17T10:00:00Z",
    "reservedUntil": "2026-06-17T12:00:00Z"
  }'
```

### Get reservation
```bash
curl http://localhost:8080/reservations/{id} \
  -H "Authorization: Bearer $TOKEN"
```

### Check in
```bash
curl -X POST http://localhost:8080/reservations/{id}/checkin \
  -H "Authorization: Bearer $TOKEN"
```

### Check out
```bash
curl -X POST http://localhost:8080/reservations/{id}/checkout \
  -H "Authorization: Bearer $TOKEN"
```

### Cancel
```bash
curl -X POST http://localhost:8080/reservations/{id}/cancel \
  -H "Authorization: Bearer $TOKEN"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5435/reservationdb` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
| `AVAILABILITY_SERVICE_URL` | `http://localhost:8084` | URL for spot state check |
