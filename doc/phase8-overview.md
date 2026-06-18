# Phase 8 — Analytics Service

## Goal
Give parking operators a read-only dashboard over all domain events: occupancy rates, revenue summaries, and violation statistics. Every event from every topic is persisted to an append-only analytics store, then queried with native SQL aggregations. No upstream service is modified.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| analytics-service | 8089 | Event ingestion, time-series store, aggregation API |
| postgres-analytics | 5439 | Append-only `analytics_events` table |

## Data Model

All domain events land in a single `analytics_events` table:

```
id           UUID          primary key
event_type   VARCHAR(60)   'reservation.created', 'payment.success', 'penalty.issued', ...
topic        VARCHAR(100)  'reservation-events', 'payment-events', 'penalty-events', 'spot-state'
user_id      UUID          nullable (spot-state events have no user)
entity_id    UUID          spotId / reservationId / paymentId / penaltyId
amount       DECIMAL(10,2) nullable — payment and penalty events only
tier         SMALLINT      nullable — penalty events only
event_time   TIMESTAMPTZ   time the original event was emitted (from payload timestamp)
received_at  TIMESTAMPTZ   time this service received the Kafka message
raw_payload  TEXT          full JSON for ad-hoc queries
```

This append-only approach means:
- No UPDATE / DELETE — the store is immutable
- Any new metric can be derived by re-querying the existing rows
- Easy to replay — if a query is wrong, re-run it against the same data

## Kafka Topics Consumed

| Topic | Events | Fields extracted |
|---|---|---|
| `reservation-events` | all reservation.* | userId, reservationId (entity_id) |
| `payment-events` | payment.success / failed / refunded | userId, paymentId (entity_id), amount |
| `penalty-events` | penalty.issued | userId, penaltyId (entity_id), amount, tier |
| `spot-state` | spot.state.changed | spotId (entity_id) |

## API Endpoints

```
GET /analytics/occupancy?date=YYYY-MM-DD   hourly reservation counts for that day
GET /analytics/revenue?period=week|month   gross revenue, refunds, net, transaction count
GET /analytics/violations                  penalty counts grouped by tier
GET /analytics/events?type=&limit=50       raw event listing (operator debugging)
```

All endpoints are JWT-protected via the gateway.

## Query Strategy

Native PostgreSQL SQL on the `analytics_events` table:

**Occupancy (hourly buckets):**
```sql
SELECT date_trunc('hour', event_time) AS hour, COUNT(*) AS reservations
FROM analytics_events
WHERE event_type = 'reservation.created'
  AND event_time >= :startOfDay AND event_time < :endOfDay
GROUP BY hour ORDER BY hour;
```

**Revenue (period summary):**
```sql
SELECT
  COALESCE(SUM(CASE WHEN event_type = 'payment.success' THEN amount ELSE 0 END), 0) AS gross,
  COALESCE(SUM(CASE WHEN event_type = 'payment.refunded' THEN amount ELSE 0 END), 0) AS refunded,
  COUNT(CASE WHEN event_type = 'payment.success' THEN 1 END) AS transaction_count
FROM analytics_events
WHERE event_type IN ('payment.success', 'payment.refunded')
  AND event_time >= :periodStart;
```

**Violations by tier:**
```sql
SELECT tier, COUNT(*) AS count
FROM analytics_events
WHERE event_type = 'penalty.issued'
GROUP BY tier ORDER BY tier;
```

## Running Phase 8

```bash
docker-compose -f infra/docker-compose.yml up --build

TOKEN="<accessToken>"

# Hourly occupancy for today
curl "http://localhost:8080/analytics/occupancy?date=2026-06-18" \
  -H "Authorization: Bearer $TOKEN"

# Revenue for current week
curl "http://localhost:8080/analytics/revenue?period=week" \
  -H "Authorization: Bearer $TOKEN"

# Violation summary
curl "http://localhost:8080/analytics/violations" \
  -H "Authorization: Bearer $TOKEN"

# Raw event listing (most recent 20)
curl "http://localhost:8080/analytics/events?limit=20" \
  -H "Authorization: Bearer $TOKEN"

# Filter by event type
curl "http://localhost:8080/analytics/events?type=payment.success&limit=10" \
  -H "Authorization: Bearer $TOKEN"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5439/analyticsdb` | JDBC URL |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
