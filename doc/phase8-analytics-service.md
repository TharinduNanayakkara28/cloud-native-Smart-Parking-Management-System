# Analytics Service — Implementation Details

## Tech Choices

| Concern | Library | Why |
|---|---|---|
| Web | Spring Boot 3 + Spring Web | REST endpoints |
| ORM | Spring Data JPA + Hibernate | Entity mapping, native queries |
| DB migrations | Flyway | Schema versioning |
| Kafka | Spring Kafka | Event consumer (4 topics) |
| Boilerplate | Lombok | |

No Redis, no outbound Kafka. This service is a pure read-from-Kafka, write-to-Postgres, query-on-demand service.

## Package Structure

```
com.smartparking.analytics
├── config/
│   └── AppConfig.java                       # ObjectMapper + Kafka consumer factory
├── controller/
│   └── AnalyticsController.java
├── dto/
│   ├── HourlyBucketDto.java                 # JPQL projection for occupancy
│   ├── OccupancyResponse.java
│   ├── RevenueResponse.java
│   ├── ViolationTierDto.java
│   ├── ViolationsResponse.java
│   └── EventRecordResponse.java
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ReservationEventConsumer.java
│   ├── PaymentEventConsumer.java
│   ├── PenaltyEventConsumer.java
│   └── SpotStateEventConsumer.java
├── model/
│   └── AnalyticsEvent.java
└── repository/
    └── AnalyticsEventRepository.java
└── service/
    └── AnalyticsService.java
```

## Key Design Decisions

### Why one table for all events?
A single `analytics_events` table keeps queries simple and the schema stable — new event types are stored as new rows, not new tables. Filtering by `event_type` is fast with an index. If query volume grows, partition by `event_time` (PostgreSQL range partitioning) — no model changes needed.

### Why native SQL instead of JPQL for aggregations?
`date_trunc` is a PostgreSQL function. JPQL's `FUNCTION()` escape works but is verbose and harder to read. Native `@Query(nativeQuery = true)` lets us write idiomatic SQL that's directly readable and testable in `psql`.

### Why Object[] mapping instead of a custom projection interface?
`@SqlResultSetMapping` adds noise. `Object[]` is cast-ugly but contained to the service layer where we know the exact column positions. For each query the positions are fixed and documented inline.

### Why no separate "aggregation tables" (materialized views)?
For a portfolio project, pre-aggregated tables add complexity without demonstrating any new pattern. The queries are fast on small datasets. If traffic warranted it, the natural next step is PostgreSQL materialized views refreshed via `pg_cron` — that's mentioned in the code comments as the upgrade path.

### Why consume `spot-state` events?
Spot state changes are raw occupancy signals — different from reservation-driven occupancy. Storing them as analytics events enables future "actual vs reserved occupancy" comparisons without re-consuming from another service.

## Response Shapes

### GET /analytics/occupancy?date=YYYY-MM-DD
```json
{
  "date": "2026-06-18",
  "totalReservations": 14,
  "hourlyBreakdown": [
    { "hour": "2026-06-18T08:00:00Z", "reservationCount": 3 },
    { "hour": "2026-06-18T09:00:00Z", "reservationCount": 7 },
    { "hour": "2026-06-18T10:00:00Z", "reservationCount": 4 }
  ]
}
```

### GET /analytics/revenue?period=week|month
```json
{
  "period": "week",
  "periodStart": "2026-06-11T00:00:00Z",
  "grossRevenue": "150.00",
  "refundedAmount": "20.00",
  "netRevenue": "130.00",
  "transactionCount": 35
}
```

### GET /analytics/violations
```json
{
  "totalViolations": 12,
  "byTier": [
    { "tier": 1, "type": "WARNING",   "count": 8 },
    { "tier": 2, "type": "FINE",      "count": 3 },
    { "tier": 3, "type": "ESCALATED", "count": 1 }
  ]
}
```

### GET /analytics/events?type=payment.success&limit=10
```json
[
  {
    "id": "uuid",
    "eventType": "payment.success",
    "topic": "payment-events",
    "userId": "uuid",
    "entityId": "uuid",
    "amount": "12.50",
    "tier": null,
    "eventTime": "2026-06-18T09:30:00Z",
    "receivedAt": "2026-06-18T09:30:01Z"
  }
]
```

## Error Responses

ProblemDetail (RFC 9457):

| Scenario | HTTP Status |
|---|---|
| Invalid date format | 400 |
| Invalid period value | 400 |
