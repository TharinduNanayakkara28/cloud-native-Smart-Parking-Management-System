# Notification Service — Implementation Details

## Tech Choices

| Concern | Library | Why |
|---|---|---|
| Web | Spring Boot 3 + Spring Web | REST endpoints |
| ORM | Spring Data JPA + Hibernate | Notification records |
| DB migrations | Flyway | Schema versioning |
| Kafka | Spring Kafka | Event consumer (3 topics) |
| Boilerplate | Lombok | |

No Redis dependency — the notification service needs no distributed state. Notifications are pure write-then-read via PostgreSQL.

## Package Structure

```
com.smartparking.notification
├── config/
│   └── AppConfig.java                     # ObjectMapper + Kafka consumer factory
├── controller/
│   └── NotificationController.java
├── dto/
│   ├── NotificationResponse.java
│   ├── ReservationEvent.java
│   ├── ReservationEventPayload.java
│   ├── PaymentEvent.java
│   ├── PaymentEventPayload.java
│   ├── PenaltyEvent.java
│   └── PenaltyEventPayload.java
├── exception/
│   └── GlobalExceptionHandler.java
├── kafka/
│   ├── ReservationEventConsumer.java
│   ├── PaymentEventConsumer.java
│   └── PenaltyEventConsumer.java
├── model/
│   └── Notification.java
├── provider/
│   ├── NotificationProvider.java          # Interface
│   └── MockNotificationProvider.java      # Default impl
├── repository/
│   └── NotificationRepository.java
└── service/
    └── NotificationService.java
```

## Key Design Decisions

### Why no Kafka publishing?
The notification service is a leaf node in the event graph. It consumes events and delivers alerts — it has no downstream services to notify. Adding a `notification.sent` event would only be useful for analytics (Phase 8), which can subscribe directly to the same upstream topics.

### Why separate consumer classes per topic instead of one multi-topic listener?
Separate consumers give independent groupId handling, cleaner error isolation, and easier per-event-type unit testing. A single multi-topic listener would mix deserialization failures across unrelated domains.

### Why a `NotificationProvider` interface?
The mock provider logs to console. A real implementation would inject `JavaMailSender` (email) or a Twilio REST client (SMS). Spring's `@Profile` annotation can select the active provider without changing the service layer:
```java
@Bean
@Profile("prod")
NotificationProvider smtpProvider(JavaMailSender mailSender) { ... }

@Bean
@Profile("!prod")
NotificationProvider mockProvider() { return new MockNotificationProvider(); }
```

### Why store all notifications in PostgreSQL rather than only delivering them?
Persistent storage enables:
- In-app notification centre (unread badge, history)
- Delivery retry (mark `delivered=false`, retry job can pick them up)
- Audit trail for compliance

### Idempotency
Each Kafka consumer wraps processing in a try-catch. Failed messages are logged with the Kafka offset — no explicit dead-letter queue in Phase 7. The notification record itself is the idempotency check (same event → same notification); duplicate Kafka delivery would create a duplicate notification, acceptable trade-off at this scale.

## Notification Model

```
id          UUID    primary key
user_id     UUID    not null
type        VARCHAR(50)   RESERVATION_CREATED | PAYMENT_SUCCESS | PENALTY_ISSUED | ...
title       VARCHAR(200)
message     TEXT
channel     VARCHAR(20)   IN_APP
read        BOOLEAN default false
created_at  TIMESTAMPTZ default now()
read_at     TIMESTAMPTZ nullable
```

## Error Responses

ProblemDetail (RFC 9457):

| Scenario | HTTP Status |
|---|---|
| Notification not found | 404 |
| Wrong owner | 403 |
