# Phase 5 — Payment Service

## Goal
Handle all financial transactions: pre-authorise payment when a reservation is created, capture the exact amount when the driver checks out, and refund on cancellation. Idempotency keys prevent double-charging if Kafka replays an event.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| payment-service | 8086 | Payment lifecycle, mock provider, Kafka consumer/producer |
| postgres-payment | 5436 | Dedicated DB for payment-service |

## Payment Lifecycle

```
reservation.created  (Kafka)
        │
        ▼
PaymentService.preAuthorise()
  ├── Idempotency check  (skip if already processed)
  ├── Calculate pre-auth amount  (reserved duration × $2/hr)
  ├── INSERT Payment(status=PENDING)
  ├── MockProvider.hold(amount, idempotencyKey)
  │     ├── success → status=HELD,  publish payment.success
  │     └── failure → status=FAILED, publish payment.failed
  └── Reservation Service picks up payment.success → ACTIVE
                                  payment.failed  → CANCELLED


reservation.completed  (Kafka)
        │
        ▼
PaymentService.captureActual()
  ├── Find Payment by reservationId (status must be HELD)
  ├── Calculate actual amount  (checkout - checkin) × $2/hr
  ├── MockProvider.capture(providerRef, actualAmount)
  └── status=CHARGED  (receipt updated; no new Kafka event needed)


reservation.cancelled  (Kafka)
        │
        ▼
PaymentService.refund()
  ├── Find Payment by reservationId (status=HELD only)
  ├── MockProvider.refund(providerRef)
  ├── status=REFUNDED
  └── publish payment.refunded
```

## Pricing Model

| Setting | Default | Description |
|---|---|---|
| `payment.hourly-rate` | `2.00` USD | Rate per hour |
| `payment.currency` | `USD` | Currency code |
| Pre-auth amount | `ceil(duration_hours) × rate` | Rounds up, min 1 hour |
| Actual charge | `ceil(actual_hours) × rate` | Based on checkin→checkout |

## Idempotency

Each payment record has a unique `idempotency_key` column. Key format: `{reservationId}:reservation.created`. Before processing any event, the service checks whether a record with that key already exists. If so, the event is a Kafka replay and is skipped.

For refunds: payment status is checked (`HELD` required) — attempting to refund a `REFUNDED` or `CHARGED` payment is a no-op.

## Mock Payment Provider

`MockPaymentProvider` implements the `PaymentProvider` interface:
- `hold(amount, key)` → returns a UUID as `providerRef` (simulates bank pre-auth)
- `capture(providerRef, amount)` → logs the capture (simulates settlement)
- `refund(providerRef)` → logs the refund

The `MOCK_FAILURE_RATE` env var (0.0–1.0, default 0.0) simulates random provider failures — useful for testing the saga compensation path.

Swap in a real `StripePaymentProvider` in Phase 9 by implementing the same interface.

## Kafka Topics

| Direction | Topic | Event |
|---|---|---|
| Consumes | `reservation-events` | `reservation.created`, `reservation.completed`, `reservation.cancelled` |
| Publishes | `payment-events` | `payment.success`, `payment.failed`, `payment.refunded` |

## API Endpoints

```
GET /payments/{reservationId}    header: X-User-Id    (receipt)
```

## Running Phase 5

```bash
docker-compose -f infra/docker-compose.yml up --build
```

End-to-end saga test:
```bash
TOKEN="<accessToken>"

# 1. Create reservation
RES=$(curl -s -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"spotId":"00000000-0000-0000-0000-000000000001","vehiclePlate":"ABC-001",
       "reservedFrom":"2026-06-17T10:00:00Z","reservedUntil":"2026-06-17T12:00:00Z"}')
ID=$(echo $RES | jq -r '.id')

# 2. Payment fires automatically (payment.success → reservation ACTIVE)
sleep 2

# 3. Check reservation is now ACTIVE
curl http://localhost:8080/reservations/$ID -H "Authorization: Bearer $TOKEN"

# 4. Check payment receipt
curl http://localhost:8080/payments/$ID -H "Authorization: Bearer $TOKEN"

# 5. Check in, then check out
curl -X POST http://localhost:8080/reservations/$ID/checkin -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8080/reservations/$ID/checkout -H "Authorization: Bearer $TOKEN"

# 6. Verify actual charge on receipt
curl http://localhost:8080/payments/$ID -H "Authorization: Bearer $TOKEN"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5436/paymentdb` | JDBC URL |
| `DB_USERNAME` | `postgres` | DB user |
| `DB_PASSWORD` | `postgres` | DB password |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
| `PAYMENT_HOURLY_RATE` | `2.00` | Dollars per hour |
| `PAYMENT_CURRENCY` | `USD` | Currency code |
| `MOCK_FAILURE_RATE` | `0.0` | Probability of mock provider failure (0.0–1.0) |
