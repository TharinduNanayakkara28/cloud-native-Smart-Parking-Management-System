# Cloud-Native Smart Parking Management System

A fully event-driven microservices portfolio project. Drivers find available parking spots, reserve them, pay, and receive real-time alerts. A software sensor simulator replaces physical hardware, publishing spot-state events to Kafka exactly as real sensors would.

---

## Architecture

```
                              ┌─────────────────────────────────┐
                              │           API Gateway           │
                              │       :8080  (JWT + rate limit) │
                              └──────┬──────────────────────────┘
                                     │
          ┌──────────┬───────────────┼──────────────┬──────────────┐
          ▼          ▼               ▼              ▼              ▼
    User Service  Simulator   Availability   Reservation     Spot Detection
      :8081        :8082        Service        Service          :8083
                                 :8084          :8085
                                     │              │
                        ─────────────────────────────────────────────────
                                     Apache Kafka (KRaft, no ZooKeeper)
                        ─────────────────────────────────────────────────
                              │         │           │         │
                           Payment   Penalty  Notification  Analytics
                           Service   Service    Service      Service
                            :8086     :8087      :8088        :8089
                                     │
                                  Redis
                               (overstay timers)

                        ┌──────────────────────────┐
                        │  Prometheus :9090         │
                        │  Grafana    :3000         │
                        └──────────────────────────┘
```

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| **api-gateway** | 8080 | JWT validation, rate limiting, X-User-Id header injection |
| **user-service** | 8081 | Registration, login, JWT issuance, vehicle management |
| **sensor-simulator** | 8082 | Simulates physical sensors; publishes `spot.occupied` / `spot.freed` |
| **spot-detection-service** | 8083 | Consumes sensor events, maintains authoritative spot state |
| **availability-service** | 8084 | Real-time spot availability (Redis cache + WebSocket push) |
| **reservation-service** | 8085 | Reservation lifecycle, distributed lock, expiry scheduler |
| **payment-service** | 8086 | Pre-auth hold → capture on checkout → refund on cancel |
| **penalty-service** | 8087 | Overstay detection (Redis timers + SCAN), 3-tier fines |
| **notification-service** | 8088 | In-app notifications from all domain events |
| **analytics-service** | 8089 | Append-only event store, occupancy/revenue/violations API |
| **prometheus** | 9090 | Metrics scraping from all services |
| **grafana** | 3000 | "Smart Parking Overview" dashboard (pre-loaded) |

---

## Quick Start

### Prerequisites
- Docker 24+ and Docker Compose v2
- 8 GB RAM recommended (14 containers)

### Start Everything

```bash
git clone <repo-url>
cd cloud-native-Smart-Parking-Management-System

docker compose -f infra/docker-compose.yml up --build
```

Wait ~60 seconds for all services to become healthy, then:

| URL | Purpose |
|---|---|
| http://localhost:8080 | API Gateway (all client calls go here) |
| http://localhost:9090 | Prometheus target status |
| http://localhost:3000 | Grafana — login: `admin` / `admin` |

---

## Walkthrough — Full Parking Flow

### 1. Register and log in

```bash
# Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123","phone":"0712345678"}'

# Login → copy the accessToken
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'

TOKEN="<accessToken from login>"
```

### 2. Start the sensor simulator

```bash
# Auto-simulate spot state changes every 2 seconds
curl -X POST http://localhost:8080/simulate/auto/start
```

### 3. Find available spots

```bash
curl "http://localhost:8080/spots/available?lat=-6.2088&lng=106.8456&radius=500" \
  -H "Authorization: Bearer $TOKEN"
```

### 4. Reserve a spot

```bash
SPOT_ID="<spotId from above>"

curl -X POST http://localhost:8080/reservations \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"spotId\": \"$SPOT_ID\",
    \"vehiclePlate\": \"ABC-001\",
    \"reservedFrom\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",
    \"reservedUntil\": \"$(date -u -v+1H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d '+1 hour' +%Y-%m-%dT%H:%M:%SZ)\"
  }"
```

The reservation saga runs automatically:
1. Reservation created (status: `PENDING`)
2. Payment service pre-authorises the amount
3. On `payment.success` → reservation moves to `ACTIVE`

### 5. Check in

```bash
RESERVATION_ID="<id from reservation response>"

curl -X POST http://localhost:8080/reservations/$RESERVATION_ID/checkin \
  -H "Authorization: Bearer $TOKEN"
```

### 6. Check out

```bash
curl -X POST http://localhost:8080/reservations/$RESERVATION_ID/checkout \
  -H "Authorization: Bearer $TOKEN"
```

Payment is captured for the actual duration used.

### 7. View notifications

```bash
curl http://localhost:8080/notifications/user/me \
  -H "Authorization: Bearer $TOKEN"
```

### 8. View analytics (operator)

```bash
# Occupancy for today
curl "http://localhost:8080/analytics/occupancy?date=$(date +%Y-%m-%d)" \
  -H "Authorization: Bearer $TOKEN"

# Revenue this week
curl "http://localhost:8080/analytics/revenue?period=week" \
  -H "Authorization: Bearer $TOKEN"

# Violation summary
curl "http://localhost:8080/analytics/violations" \
  -H "Authorization: Bearer $TOKEN"
```

---

## Kafka Event Flow

```
Simulator ──► spot-events ──► Spot Detection ──► spot-state ──► Availability
                                                                 Analytics

Client ──► Reservation Service ──► reservation-events ──► Availability (state)
                                                       ──► Payment (saga)
                                                       ──► Penalty (timer)
                                                       ──► Notification
                                                       ──► Analytics

Payment Service ──► payment-events ──► Reservation (saga)
                                   ──► Notification
                                   ──► Analytics

Penalty Service ──► penalty-events ──► Notification
                                   ──► Analytics
```

| Topic | Events |
|---|---|
| `spot-events` | `spot.occupied`, `spot.freed` |
| `spot-state` | `spot.state.changed` |
| `reservation-events` | `reservation.created`, `reservation.active`, `reservation.completed`, `reservation.expired`, `reservation.cancelled` |
| `payment-events` | `payment.success`, `payment.failed`, `payment.refunded` |
| `penalty-events` | `penalty.issued` |

---

## API Reference

### Auth
```
POST /auth/register      { name, email, password, phone }
POST /auth/login         { email, password }
POST /auth/refresh       { refreshToken }
```

### Users & Vehicles
```
GET  /users/me                               header: Authorization
POST /users/me/vehicles  { plate, make, model }
GET  /users/me/vehicles
```

### Availability
```
GET  /spots/available?lat=&lng=&radius=      header: Authorization
GET  /spots/state                            header: Authorization
WS   /ws/availability                        real-time spot updates
```

### Reservations
```
POST /reservations                           { spotId, vehiclePlate, reservedFrom, reservedUntil }
GET  /reservations/{id}
GET  /reservations/user/me
POST /reservations/{id}/cancel
POST /reservations/{id}/checkin
POST /reservations/{id}/checkout
```

### Payments
```
GET  /payments/{reservationId}
```

### Penalties
```
GET  /penalties/user/me
POST /penalties/{id}/pay
```

### Notifications
```
GET  /notifications/user/me
GET  /notifications/user/me/unread-count
POST /notifications/{id}/read
POST /notifications/user/me/read-all
```

### Analytics
```
GET  /analytics/occupancy?date=YYYY-MM-DD
GET  /analytics/revenue?period=week|month
GET  /analytics/violations
GET  /analytics/events?type=&limit=50
```

### Simulator (admin)
```
POST /simulate/auto/start
POST /simulate/auto/stop
POST /simulate/spot/{id}/occupy
POST /simulate/spot/{id}/free
GET  /simulate/spots
```

---

## Design Patterns

| Pattern | Where |
|---|---|
| **Saga (choreography)** | Reservation → Payment → Reservation (compensating events on failure) |
| **Distributed Lock** | `SETNX lock:spot:{spotId}` in Redis before creating reservation |
| **Outbox / Event-Driven** | All state changes published to Kafka; consumers are independent |
| **CQRS-lite** | Availability reads from Redis (cache), writes via Kafka events |
| **Append-only Event Store** | Analytics service: never updates rows, derives all metrics from event log |
| **Mock Provider** | Payment and Notification providers behind an interface — swap for real Stripe/Twilio |
| **Scheduled Jobs** | Reservation expiry every 60s; overstay check every 120s |
| **ProblemDetail (RFC 9457)** | Consistent error responses across all services |

---

## Monitoring

| URL | What you'll see |
|---|---|
| http://localhost:9090/targets | Prometheus scrape status for all 10 services |
| http://localhost:3000 | Grafana (admin/admin) |
| http://localhost:3000/d/smart-parking-overview | Pre-loaded "Smart Parking Overview" dashboard |

Dashboard panels:
- Service health (up/down per service)
- HTTP request rate and error rate
- P99 latency
- JVM heap usage
- Kafka consumer lag
- HikariCP connection pool

---

## Databases

| Database | Host Port | Service |
|---|---|---|
| postgres-user | 5432 | user-service |
| postgres-spot-detection | 5433 | spot-detection-service |
| postgres-availability | 5434 | availability-service |
| postgres-reservation | 5435 | reservation-service |
| postgres-payment | 5436 | payment-service |
| postgres-penalty | 5437 | penalty-service |
| postgres-notification | 5438 | notification-service |
| postgres-analytics | 5439 | analytics-service |
| redis | 6379 | availability-service, reservation-service, penalty-service |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| API Gateway | Spring Cloud Gateway |
| Message Broker | Apache Kafka (KRaft — no ZooKeeper) |
| Databases | PostgreSQL 16 + PostGIS (geospatial) |
| Cache / Lock | Redis 7 |
| Auth | JWT (HS256, JJWT 0.12.5) |
| Schema Migrations | Flyway |
| Observability | Micrometer + Prometheus + Grafana |
| Containers | Docker + Docker Compose |

---

## Project Structure

```
.
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   ├── sensor-simulator/
│   ├── spot-detection-service/
│   ├── availability-service/
│   ├── reservation-service/
│   ├── payment-service/
│   ├── penalty-service/
│   ├── notification-service/
│   └── analytics-service/
├── infra/
│   ├── docker-compose.yml
│   ├── prometheus/
│   │   └── prometheus.yml
│   └── grafana/
│       ├── provisioning/
│       │   ├── datasources/prometheus.yml
│       │   └── dashboards/provider.yml
│       └── dashboards/smart-parking.json
├── doc/
│   ├── phase1-overview.md … phase9-monitoring.md
└── README.md
```

---

## Stopping

```bash
docker compose -f infra/docker-compose.yml down

# Remove volumes too (clears all data)
docker compose -f infra/docker-compose.yml down -v
```
