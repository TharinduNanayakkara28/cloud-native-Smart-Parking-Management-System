# Phase 6 — Penalty Service

## Goal
Detect overstaying drivers and escalate fines in three tiers. A Redis hash tracks every active parking session. A scheduled job scans those sessions every 2 minutes and issues penalties when a driver exceeds their reserved window — without querying the reservation or spot-detection service.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| penalty-service | 8087 | Overstay tracking (Redis), scheduled checks, fine issuance, penalty CRUD |
| postgres-penalty | 5437 | Durable penalty records |
| redis | 6379 | Overstay timer store — already running |

## Overstay Tracking Design

```
reservation.active  ──► Redis SET overstay:{reservationId} = JSON(record)  TTL=24h
                             {reservationId, userId, spotId,
                              reservedUntil, lastTierIssued=0}

reservation.completed /
reservation.cancelled /
reservation.expired ──► Redis DEL overstay:{reservationId}

@Scheduled every 120s
  SCAN overstay:*  (cursor-based, non-blocking)
  For each key:
    overdueMinutes = now − reservedUntil
    if overdueMinutes < 0  → still within window, skip
    newTier = tier(overdueMinutes)
    if newTier > lastTierIssued:
      for tier in (lastTierIssued+1)..newTier:
        INSERT penalty record
        publish penalty.issued
      update lastTierIssued in Redis
```

## Penalty Tiers

| Tier | When | Type | Amount | Action |
|---|---|---|---|---|
| 1 | 0–15 min over | WARNING | $0.00 | Record + notify (no financial charge) |
| 2 | 15–60 min over | FINE | $10.00 | Record + publish `penalty.issued` |
| 3 | 60+ min over | ESCALATED | $25.00 | Record + publish `penalty.issued` |

Each tier is issued exactly once per session. If the scheduler runs while a driver is at Tier 1 and later catches them at Tier 3, all missing tiers (1, 2, 3) are issued in sequence.

## Redis Key Schema

| Key | Value | TTL |
|---|---|---|
| `overstay:{reservationId}` | JSON `OverstayRecord` | 24 hours |

The 24-hour TTL is a safety cleanup. Keys are normally deleted when the driver completes or cancels their reservation.

## Penalty Status Machine

```
ISSUED ──► PAID
       └──► DISPUTED ──► WAIVED
                     └──► ISSUED (dispute rejected)
```

Phase 6 implements: `ISSUED` → `PAID` via `POST /penalties/{id}/pay`.

## Kafka Events

| Direction | Topic | Event | When |
|---|---|---|---|
| Consumes | `reservation-events` | `reservation.active` | Start timer |
| Consumes | `reservation-events` | `reservation.completed` | Cancel timer |
| Consumes | `reservation-events` | `reservation.cancelled` | Cancel timer |
| Consumes | `reservation-events` | `reservation.expired` | Cancel timer |
| Publishes | `penalty-events` | `penalty.issued` | Each tier escalation |

## API Endpoints

```
GET  /penalties/user/me      header: X-User-Id   (all penalties for user)
POST /penalties/{id}/pay     header: X-User-Id   (mark penalty paid)
```

## Running Phase 6

```bash
docker-compose -f infra/docker-compose.yml up --build
```

Simulate an overstay:
```bash
TOKEN="<accessToken>"

# 1. Create reservation expiring in the past (scheduler will detect overstay)
curl -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"spotId":"00000000-0000-0000-0000-000000000001","vehiclePlate":"ABC-001",
       "reservedFrom":"2026-06-17T09:00:00Z","reservedUntil":"2026-06-17T09:01:00Z"}'

# 2. After payment.success fires, reservation becomes ACTIVE (overstay timer starts)
# 3. Wait up to 2 min for scheduler to detect overstay

# 4. Check penalties
curl http://localhost:8080/penalties/user/me -H "Authorization: Bearer $TOKEN"

# 5. Pay a penalty
curl -X POST http://localhost:8080/penalties/{id}/pay -H "Authorization: Bearer $TOKEN"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5437/penaltydb` | JDBC URL |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
| `PENALTY_TIER2_AMOUNT` | `10.00` | Fine amount (USD) |
| `PENALTY_TIER3_AMOUNT` | `25.00` | Escalated fine amount (USD) |
