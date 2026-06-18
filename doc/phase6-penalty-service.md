# Penalty Service — Implementation Details

## Tech Choices

| Concern | Library | Why |
|---|---|---|
| Web | Spring Boot 3 + Spring Web | REST endpoints |
| ORM | Spring Data JPA + Hibernate | Penalty records |
| DB migrations | Flyway | Schema versioning |
| Redis client | Spring Data Redis (`StringRedisTemplate`) | Overstay timer store + SCAN |
| Kafka | Spring Kafka | Event consumer + producer |
| Scheduling | Spring Scheduling (`@Scheduled`) | Every-2-min overstay check |
| Boilerplate | Lombok | |

## Package Structure

```
com.smartparking.penalty
├── config/
│   └── AppConfig.java                 # ObjectMapper + KafkaTemplate beans
├── controller/
│   └── PenaltyController.java
├── dto/
│   ├── OverstayRecord.java            # Stored in Redis as JSON
│   ├── PenaltyResponse.java
│   ├── ReservationEvent.java
│   └── ReservationEventPayload.java
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ReservationEventConsumer.java
│   └── PenaltyEventPublisher.java
├── model/
│   ├── Penalty.java
│   └── PenaltyStatus.java
├── repository/
│   └── PenaltyRepository.java
├── scheduler/
│   └── OverstayCheckScheduler.java
└── service/
    └── PenaltyService.java
```

## Key Design Decisions

### Why Redis for overstay timers, not the penalty DB?
The overstay tracker is ephemeral: created when a session starts, deleted when it ends. If the service restarts, Redis still holds the timers. PostgreSQL is reserved for durable penalty records (fines, disputes, payment history).

### Why cursor-based SCAN instead of KEYS?
`KEYS overstay:*` blocks Redis until it finishes scanning all keys. `SCAN` with a cursor iterates incrementally — safe to use on a shared Redis instance. Spring Data Redis provides `StringRedisTemplate.scan()` which returns an auto-closing `Cursor<String>`.

### Why issue all missed tiers, not just the current one?
If the scheduler misses a run (restart, GC pause), a driver could jump from Tier 0 to Tier 3 without Tier 1 or 2 records. The `for (tier = lastTierIssued + 1; tier <= newTier; tier++)` loop ensures the audit trail is complete for notification and analytics.

### Why a 24-hour TTL on overstay keys?
If the penalty service crashes between the driver completing the reservation and the Kafka `reservation.completed` event being consumed, the key would stay forever. The 24-hour TTL is a safety cleanup — after 24 hours, any uncleaned key is stale regardless of status.

### Why is `GET /penalties/user/me` not `/user/:userId`?
The service reads `X-User-Id` from the gateway header instead of accepting a user ID in the path. This prevents users from querying other users' penalties. The gateway guarantees the header is authentic.

## Tier Logic

```
overdueMinutes = (now - reservedUntil).toMinutes()

tier:
  overdueMinutes in [0, 15)   → Tier 1 (WARNING,   $0.00)
  overdueMinutes in [15, 60)  → Tier 2 (FINE,      $10.00)
  overdueMinutes >= 60        → Tier 3 (ESCALATED,  $25.00)
```

The scheduler issues every tier between `lastTierIssued + 1` and the current tier on each pass.

## OverstayRecord (Redis value)

```json
{
  "reservationId": "uuid",
  "userId":        "uuid",
  "spotId":        "uuid",
  "reservedUntil": "2026-06-17T12:00:00Z",
  "lastTierIssued": 0
}
```

`lastTierIssued` is updated in Redis after each penalty batch to prevent double-issuing.

## penalty-events Kafka Payload

```json
{
  "eventType": "penalty.issued",
  "timestamp": "2026-06-17T12:20:00Z",
  "payload": {
    "penaltyId":     "uuid",
    "reservationId": "uuid",
    "userId":        "uuid",
    "spotId":        "uuid",
    "type":          "WARNING | FINE | ESCALATED",
    "tier":          1,
    "amount":        "0.00",
    "status":        "ISSUED"
  }
}
```

## Error Responses

ProblemDetail (RFC 9457):

| Scenario | HTTP Status |
|---|---|
| Penalty not found | 404 |
| Wrong owner | 403 |
| Penalty already paid | 409 |
