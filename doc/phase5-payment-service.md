# Payment Service — Implementation Details

## Tech Choices

| Concern | Library | Why |
|---|---|---|
| Web | Spring Boot 3 + Spring Web | REST receipt endpoint |
| ORM | Spring Data JPA + Hibernate | Repository pattern |
| DB migrations | Flyway | Schema versioning |
| Kafka | Spring Kafka | Event consumer + producer |
| Validation | Jakarta Bean Validation | |
| Boilerplate | Lombok | |

## Package Structure

```
com.smartparking.payment
├── config/
│   └── KafkaConfig.java               # Consumer factory (String deserialization)
├── controller/
│   └── PaymentController.java         # GET /payments/{reservationId}
├── dto/
│   ├── PaymentResponse.java
│   ├── ReservationEvent.java          # Inbound event wrapper
│   └── ReservationEventPayload.java   # Subset of fields we care about
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ReservationEventConsumer.java  # Consumes reservation-events
│   └── PaymentEventPublisher.java     # Publishes payment-events
├── model/
│   ├── Payment.java
│   └── PaymentStatus.java             # PENDING | HELD | CHARGED | REFUNDED | FAILED
├── provider/
│   ├── PaymentProvider.java           # Interface
│   └── MockPaymentProvider.java       # Always-succeeds implementation
├── repository/
│   └── PaymentRepository.java
└── service/
    └── PaymentService.java
```

## Database Schema

```sql
payments
  id               UUID PK
  reservation_id   UUID NOT NULL
  user_id          UUID NOT NULL
  amount           DECIMAL(10,2)     -- pre-auth amount; updated to actual on capture
  currency         VARCHAR(5)
  status           VARCHAR(20)       -- PENDING | HELD | CHARGED | REFUNDED | FAILED
  provider_ref     VARCHAR(100)      -- reference returned by payment provider
  idempotency_key  VARCHAR(200) UNIQUE  -- {reservationId}:reservation.created
  created_at       TIMESTAMPTZ
  updated_at       TIMESTAMPTZ
```

## Key Design Decisions

### Why only one payment record per reservation?
The payment record is created on `reservation.created` and updated (not replaced) on `reservation.completed` (capture) and `reservation.cancelled` (refund). A single record makes the receipt endpoint simple: `findByReservationId`.

### Why idempotency key on creation only?
Kafka can redeliver a message if the consumer crashes after processing but before committing the offset. The idempotency_key (`{reservationId}:reservation.created`) prevents a double pre-auth. For capture and refund, the status check (`HELD` required) provides the same protection.

### Why a PaymentProvider interface?
Isolates the payment provider from business logic. Swapping mock for Stripe in Phase 9 means writing one new class (`StripePaymentProvider`) and changing one Spring bean — zero changes to `PaymentService`.

### Saga compensation
If `MockPaymentProvider.hold()` throws, `PaymentService` publishes `payment.failed`. The `PaymentEventConsumer` in reservation-service then cancels the reservation. No two-phase commit, no distributed transaction — compensating events handle rollback.

## PaymentProvider Contract

```java
interface PaymentProvider {
    // Pre-authorise: holds funds. Returns providerRef for future capture/refund.
    String hold(BigDecimal amount, String idempotencyKey);

    // Capture: settles the pre-authorised amount (may differ from held amount).
    void capture(String providerRef, BigDecimal amount);

    // Refund: returns the held/charged amount.
    void refund(String providerRef);
}
```

## Pricing Calculation

```
preAuthAmount  = max(ceil((reservedUntil - reservedFrom).toHours()), 1) × hourlyRate
actualAmount   = max(ceil((checkedOutAt  - checkedInAt).toHours()),  1) × hourlyRate

// If checkedInAt/checkedOutAt are null (driver never arrived):
actualAmount   = preAuthAmount
```

## Error Response Format

ProblemDetail (RFC 9457) for all errors:

| Scenario | HTTP Status |
|---|---|
| Payment not found | 404 |
| Wrong owner | 403 |
| Internal provider error (non-saga path) | 500 |
