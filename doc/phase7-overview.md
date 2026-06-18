# Phase 7 — Notification Service

## Goal
Deliver driver-facing alerts for every significant lifecycle event: reservation confirmed, payment charged, overstay warning, fine issued. Notifications are stored in-app and optionally delivered via a swappable external provider (email, SMS). The service is a pure consumer — it publishes no Kafka events of its own.

## Services in This Phase

| Service | Port | Responsibility |
|---|---|---|
| notification-service | 8088 | Event → notification routing, in-app store, delivery via provider |
| postgres-notification | 5438 | Durable notification records |

## Event → Notification Mapping

| Event | Title | Message |
|---|---|---|
| `reservation.created` | Reservation Confirmed | "Your parking spot is reserved until {reservedUntil}" |
| `reservation.active` | Check-in Confirmed | "You have successfully checked in" |
| `reservation.completed` | Check-out Confirmed | "Your parking session has ended. Thank you!" |
| `reservation.expired` | Reservation Expired | "Your reservation has expired and the spot has been released" |
| `reservation.cancelled` | Reservation Cancelled | "Your reservation has been cancelled" |
| `payment.success` | Payment Successful | "Payment of {currency} {amount} confirmed" |
| `payment.failed` | Payment Failed | "Your payment could not be processed. Please try again" |
| `payment.refunded` | Refund Processed | "A refund of {currency} {amount} has been issued" |
| `penalty.issued` (Tier 1) | Overstay Warning | "You have exceeded your reserved window. Please vacate the spot" |
| `penalty.issued` (Tier 2+) | Overstay Fine Issued | "An overstay fine of {currency} {amount} has been issued (Tier {tier})" |

## Architecture

```
reservation-events  ──►  ReservationEventConsumer ──►
payment-events      ──►  PaymentEventConsumer     ──► NotificationService ──► PostgreSQL
penalty-events      ──►  PenaltyEventConsumer     ──►         │
                                                              ▼
                                                    NotificationProvider
                                                    (MockNotificationProvider)
                                                    [swap for Twilio/SMTP in prod]
```

## Delivery Channels

| Channel | Phase 7 Implementation |
|---|---|
| IN_APP | Stored in `notifications` table; served via REST |
| EMAIL | Logged to console (MockNotificationProvider); inject real SMTP bean for prod |
| SMS | Logged to console (MockNotificationProvider); inject Twilio bean for prod |

The `NotificationProvider` interface decouples delivery logic from routing logic — switching from mock to real delivery requires adding a new `@Profile("prod")` `@Bean`, no other changes.

## API Endpoints

```
GET  /notifications/user/me                header: X-User-Id  (all notifications, newest first)
GET  /notifications/user/me/unread-count   header: X-User-Id  (unread count)
POST /notifications/{id}/read              header: X-User-Id  (mark one as read)
POST /notifications/user/me/read-all       header: X-User-Id  (mark all as read)
```

## Running Phase 7

```bash
docker-compose -f infra/docker-compose.yml up --build

TOKEN="<accessToken>"

# Check notifications after making a reservation → payment flow
curl http://localhost:8080/notifications/user/me \
  -H "Authorization: Bearer $TOKEN"

# Mark as read
curl -X POST http://localhost:8080/notifications/{id}/read \
  -H "Authorization: Bearer $TOKEN"

# Unread badge count
curl http://localhost:8080/notifications/user/me/unread-count \
  -H "Authorization: Bearer $TOKEN"
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5438/notificationdb` | JDBC URL |
| `KAFKA_BROKER` | `localhost:9092` | Kafka bootstrap server |
| `NOTIFICATION_PROVIDER` | `mock` | `mock` logs to console; swap for real provider |
