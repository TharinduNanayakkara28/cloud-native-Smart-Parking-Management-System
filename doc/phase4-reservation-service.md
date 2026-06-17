# Reservation Service — Implementation Details

## Tech Choices

| Concern | Library | Why |
|---|---|---|
| Web | Spring Boot 3 + Spring Web | REST endpoints |
| ORM | Spring Data JPA + Hibernate | Repository pattern |
| DB migrations | Flyway | Schema versioning |
| Redis client | Spring Data Redis | Distributed lock via `StringRedisTemplate` |
| Kafka | Spring Kafka | Event producer + consumer |
| Scheduling | Spring Scheduling (`@Scheduled`) | Expiry job |
| HTTP client | `RestTemplate` | Synchronous call to availability-service |
| Validation | Jakarta Bean Validation | `@Valid` on request DTOs |
| Boilerplate | Lombok | Reduces getter/setter noise |

## Package Structure

```
com.smartparking.reservation
├── config/
│   ├── KafkaConfig.java           # JsonSerializer/Deserializer setup
│   └── RedisConfig.java           # StringRedisTemplate + Lua script bean
├── controller/
│   └── ReservationController.java # All reservation endpoints
├── dto/
│   ├── CreateReservationRequest.java
│   ├── ReservationResponse.java
│   ├── SpotStateResponse.java     # Response from availability-service
│   ├── ReservationEvent.java      # Outbound Kafka event wrapper
│   ├── ReservationEventPayload.java
│   ├── PaymentEvent.java          # Inbound Kafka event wrapper
│   └── PaymentEventPayload.java
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ReservationEventPublisher.java
│   └── PaymentEventConsumer.java
├── model/
│   ├── Reservation.java
│   └── ReservationStatus.java     # Enum: PENDING | ACTIVE | COMPLETED | EXPIRED | CANCELLED
├── repository/
│   └── ReservationRepository.java
├── scheduler/
│   └── ReservationExpiryScheduler.java
└── service/
    ├── AvailabilityClient.java    # HTTP call to availability-service
    └── ReservationService.java
```

## Database Schema (Flyway V1)

```sql
reservations  -- one row per reservation attempt
```

All PKs are `UUID`. `status` is stored as VARCHAR and validated by the Java enum.

## Distributed Lock — Lua Script (Atomic Compare-and-Delete)

The release operation must be atomic: read the current value, compare to the caller's value, delete only if matching. A non-atomic get-then-delete has a race window where the TTL expires between the get and delete, and a new owner acquires the lock just before the delete fires.

```lua
if redis.call('get', KEYS[1]) == ARGV[1]
  then return redis.call('del', KEYS[1])
  else return 0
end
```

Spring Data Redis executes this as a server-side script via `RedisTemplate.execute(RedisScript, ...)`.

## Key Design Decisions

### Why check availability before locking?
The lock has a 10-second TTL — holding it while calling the availability service would waste the window. Check first (optimistic), lock second (pessimistic). If the spot becomes occupied between the check and the lock acquire, the next sensor event updates Redis and subsequent reservations will see the correct state.

### Why trust X-User-Id header?
The reservation service sits behind the API Gateway. The gateway validates the JWT and injects `X-User-Id` before forwarding. Adding JWT parsing to every downstream service would duplicate logic. In production, mTLS between gateway and services ensures the header cannot be spoofed.

### Why not publish reservation.active on checkin?
`reservation.active` is a payment confirmation event — it tells the Penalty Service to start the overstay timer. Physical check-in time is tracked separately via `checked_in_at`. When the Payment Service is built (Phase 5), `payment.success` triggers `reservation.active`, not the check-in endpoint. For Phase 4 testing (no payment service), the check-in endpoint transitions PENDING → ACTIVE and publishes `reservation.active` as a fallback.

## API Endpoints

```
POST   /reservations                  body: {spotId, vehiclePlate, reservedFrom, reservedUntil}
GET    /reservations/{id}             header: X-User-Id
GET    /reservations/user/me          header: X-User-Id
POST   /reservations/{id}/cancel      header: X-User-Id
POST   /reservations/{id}/checkin     header: X-User-Id
POST   /reservations/{id}/checkout    header: X-User-Id
```

## Error Responses

All errors use `ProblemDetail` (RFC 9457):

| Scenario | HTTP Status |
|---|---|
| Spot not FREE | 409 |
| Redis lock held | 409 |
| Reservation not found | 404 |
| Wrong owner | 403 |
| Invalid status transition | 409 |
| Validation failure | 400 |
