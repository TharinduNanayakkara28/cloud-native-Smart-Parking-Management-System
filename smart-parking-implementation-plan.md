# Smart Parking Management System — Implementation Plan

> Microservice-based portfolio project | Simulated sensor layer | No physical hardware required

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture Summary](#2-architecture-summary)
3. [Tech Stack](#3-tech-stack)
4. [Services Breakdown](#4-services-breakdown)
5. [Database Design (per service)](#5-database-design-per-service)
6. [Message Broker Events](#6-message-broker-events)
7. [Build Order](#7-build-order)
8. [Phase-by-Phase Implementation](#8-phase-by-phase-implementation)
9. [API Endpoints Reference](#9-api-endpoints-reference)
10. [Simulation Strategy](#10-simulation-strategy)
11. [Inter-service Communication Patterns](#11-inter-service-communication-patterns)
12. [Folder Structure](#12-folder-structure)
13. [Testing Strategy](#13-testing-strategy)
14. [Deployment Plan](#14-deployment-plan)
15. [Interview Talking Points](#15-interview-talking-points)

---

## 1. Project Overview

A cloud-native Smart Parking Management System built with microservices. Drivers can find available nearby spots, reserve them, pay, and receive alerts. A software simulator replaces physical sensors, publishing spot state change events to a message broker exactly as real hardware would.

**Goals**
- Demonstrate microservice boundaries, event-driven architecture, and distributed system patterns
- Showcase real engineering challenges: distributed locking, saga pattern, geospatial queries, scheduled jobs
- Fully runnable locally with Docker Compose; deployable to any cloud

---

## 2. Architecture Summary

```
Driver App (REST / WebSocket client)
        │
        ▼
   API Gateway  ◄──── Sensor Simulator (publishes spot events)
        │
   ┌────┴─────────────────────────────────┐
   │                                      │
Spot Detection   Availability   Reservation   Payment   Penalty
   Service         Service        Service     Service   Service
   │                                      │
   └──────────────┬───────────────────────┘
                  ▼
           Message Broker (Kafka)
                  │
       ┌──────────┼──────────┐
       ▼          ▼          ▼
 Notification  Analytics  User Service
  Service      Service
```

---

## 3. Tech Stack

| Layer | Technology | Reason |
|---|---|---|
| Language | Node.js (TypeScript) or Java (Spring Boot) | Widely used; great microservice support |
| API Gateway | Kong or custom Express gateway | Routing, auth, rate limiting |
| Message Broker | Apache Kafka | High-throughput event streaming |
| Databases | PostgreSQL + PostGIS | Relational + geospatial queries |
| Cache | Redis | Distributed locks, spot state cache |
| Containerisation | Docker + Docker Compose | Local orchestration |
| Orchestration | Kubernetes (optional, deployment phase) | Production scaling |
| WebSockets | Socket.io or native WS | Real-time availability push |
| Auth | JWT (access + refresh tokens) | Stateless auth across services |
| API Docs | Swagger / OpenAPI | Auto-generated per service |
| Monitoring | Prometheus + Grafana | Metrics dashboard |
| Logging | Winston + ELK stack | Centralised log aggregation |

---

## 4. Services Breakdown

### 4.1 Sensor Simulator Service
**Purpose:** Replaces physical sensors. Publishes spot state change events to Kafka.

Responsibilities:
- Maintain a list of all parking spots and their current simulated state
- Randomly flip spot states (free ↔ occupied) on a configurable interval
- Expose a REST endpoint to manually trigger state changes (for demos)
- Publish `spot.occupied` and `spot.freed` events to Kafka topic `spot-events`

### 4.2 Spot Detection Service
**Purpose:** Consumes raw sensor events and maintains authoritative spot state.

Responsibilities:
- Subscribe to `spot-events` Kafka topic
- Persist spot state changes to its own PostgreSQL database
- Publish normalised `spot.state.changed` events downstream
- Expose REST endpoints for admin queries on raw spot data

### 4.3 Availability Service
**Purpose:** Answers "which spots are free near me?" in real time.

Responsibilities:
- Subscribe to `spot.state.changed` events and update availability state
- Cache current availability in Redis for fast reads
- Expose geospatial query endpoint: `GET /spots/available?lat=&lng=&radius=`
- Push real-time availability updates to connected clients via WebSocket

### 4.4 Reservation Service
**Purpose:** Manages the full reservation lifecycle.

Responsibilities:
- Handle `POST /reservations` — acquire distributed lock on spot via Redis, create reservation
- Handle reservation expiry — scheduled job releases spot if driver does not arrive within N minutes
- Publish `reservation.created`, `reservation.expired`, `reservation.cancelled` events
- Coordinate with Payment Service via saga pattern

### 4.5 Payment Service
**Purpose:** Handles all financial transactions.

Responsibilities:
- Process payment on reservation confirmation
- Hold payment in escrow until session ends, then charge actual duration
- Handle refunds on cancellations
- Publish `payment.success`, `payment.failed`, `payment.refunded` events
- Integrate with a mock payment provider (Stripe test mode)

### 4.6 Penalty / Violation Service
**Purpose:** Detects and escalates overstay violations.

Responsibilities:
- Subscribe to `reservation.active` events to start overstay timer
- Detect when a spot remains occupied beyond the reserved window
- Escalate fines in tiers (warning → fine level 1 → fine level 2)
- Publish `penalty.issued` events
- Expose admin endpoint to list and resolve violations

### 4.7 Notification Service
**Purpose:** Sends driver-facing alerts.

Responsibilities:
- Subscribe to all domain events relevant to the driver
- Send push notifications / SMS / email for: spot ready, reservation expiring soon, payment confirmed, overstay warning, fine issued
- Use a queue to avoid blocking upstream services

### 4.8 User Service
**Purpose:** Manages driver accounts and vehicle profiles.

Responsibilities:
- Registration, login, JWT issuance
- Vehicle management (plate numbers, linked to reservations)
- Reservation and payment history
- Profile preferences

### 4.9 Analytics Service
**Purpose:** Provides operational insights to parking operators.

Responsibilities:
- Subscribe to all domain events and persist to analytics store
- Compute occupancy rates, peak hours, revenue per zone
- Expose dashboard endpoints for an operator UI

---

## 5. Database Design (per service)

Each service owns its own database — no shared schemas.

### Spot Detection DB
```sql
CREATE TABLE spots (
  id            UUID PRIMARY KEY,
  lot_id        UUID NOT NULL,
  spot_number   VARCHAR(10) NOT NULL,
  floor         INT DEFAULT 1,
  state         VARCHAR(20) NOT NULL DEFAULT 'free', -- free | occupied | reserved
  last_updated  TIMESTAMPTZ DEFAULT NOW(),
  location      GEOGRAPHY(POINT, 4326)  -- PostGIS
);

CREATE TABLE spot_events (
  id         UUID PRIMARY KEY,
  spot_id    UUID REFERENCES spots(id),
  state      VARCHAR(20),
  source     VARCHAR(50), -- sensor | simulator | manual
  created_at TIMESTAMPTZ DEFAULT NOW()
);
```

### Reservation DB
```sql
CREATE TABLE reservations (
  id             UUID PRIMARY KEY,
  user_id        UUID NOT NULL,
  spot_id        UUID NOT NULL,
  vehicle_plate  VARCHAR(20),
  status         VARCHAR(20) DEFAULT 'pending', -- pending | active | completed | expired | cancelled
  reserved_from  TIMESTAMPTZ,
  reserved_until TIMESTAMPTZ,
  checked_in_at  TIMESTAMPTZ,
  checked_out_at TIMESTAMPTZ,
  created_at     TIMESTAMPTZ DEFAULT NOW()
);
```

### Payment DB
```sql
CREATE TABLE payments (
  id              UUID PRIMARY KEY,
  reservation_id  UUID NOT NULL,
  user_id         UUID NOT NULL,
  amount          DECIMAL(10,2),
  currency        VARCHAR(5) DEFAULT 'USD',
  status          VARCHAR(20) DEFAULT 'pending', -- pending | held | charged | refunded | failed
  provider_ref    VARCHAR(100),
  created_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### Penalty DB
```sql
CREATE TABLE penalties (
  id             UUID PRIMARY KEY,
  reservation_id UUID NOT NULL,
  user_id        UUID NOT NULL,
  spot_id        UUID NOT NULL,
  type           VARCHAR(30), -- overstay | no_show
  tier           INT DEFAULT 1,
  amount         DECIMAL(10,2),
  status         VARCHAR(20) DEFAULT 'issued', -- issued | paid | disputed | waived
  issued_at      TIMESTAMPTZ DEFAULT NOW()
);
```

### User DB
```sql
CREATE TABLE users (
  id           UUID PRIMARY KEY,
  name         VARCHAR(100),
  email        VARCHAR(150) UNIQUE,
  password     VARCHAR(255),
  phone        VARCHAR(20),
  created_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE vehicles (
  id           UUID PRIMARY KEY,
  user_id      UUID REFERENCES users(id),
  plate        VARCHAR(20) UNIQUE,
  make         VARCHAR(50),
  model        VARCHAR(50)
);
```

---

## 6. Message Broker Events

All events are published to Kafka. Format: JSON with `eventType`, `timestamp`, `payload`.

| Topic | Event | Publisher | Consumers |
|---|---|---|---|
| `spot-events` | `spot.occupied` | Simulator | Spot Detection |
| `spot-events` | `spot.freed` | Simulator | Spot Detection |
| `spot-state` | `spot.state.changed` | Spot Detection | Availability, Analytics |
| `reservation-events` | `reservation.created` | Reservation | Payment, Notification, Analytics |
| `reservation-events` | `reservation.expired` | Reservation | Availability, Notification, Analytics |
| `reservation-events` | `reservation.cancelled` | Reservation | Payment, Availability, Notification |
| `reservation-events` | `reservation.active` | Reservation | Penalty, Analytics |
| `reservation-events` | `reservation.completed` | Reservation | Payment, Analytics |
| `payment-events` | `payment.success` | Payment | Reservation, Notification, Analytics |
| `payment-events` | `payment.failed` | Payment | Reservation, Notification |
| `payment-events` | `payment.refunded` | Payment | Notification, Analytics |
| `penalty-events` | `penalty.issued` | Penalty | Notification, Analytics |

---

## 7. Build Order

Build iteratively — each phase is independently runnable.

```
Phase 1 → User Service + API Gateway           (auth foundation)
Phase 2 → Sensor Simulator + Spot Detection    (data source)
Phase 3 → Availability Service                 (core query)
Phase 4 → Reservation Service                  (core feature)
Phase 5 → Payment Service                      (monetisation)
Phase 6 → Penalty Service                      (enforcement)
Phase 7 → Notification Service                 (driver UX)
Phase 8 → Analytics Service                    (operator UX)
Phase 9 → Monitoring + Deployment              (production-ready)
```

---

## 8. Phase-by-Phase Implementation

### Phase 1 — User Service + API Gateway (Week 1)

**User Service**
- [ ] Set up Node.js/TypeScript project with Express
- [ ] Connect to PostgreSQL; run migrations for `users` and `vehicles` tables
- [ ] Implement `POST /auth/register`, `POST /auth/login`
- [ ] Issue JWT access token (15 min) + refresh token (7 days)
- [ ] Implement `GET /users/me`, `POST /vehicles`, `GET /vehicles`
- [ ] Write unit tests for auth logic

**API Gateway**
- [ ] Set up Express gateway (or configure Kong)
- [ ] Route `/auth/*` and `/users/*` to User Service
- [ ] Add JWT validation middleware
- [ ] Add rate limiting (express-rate-limit or Kong plugin)
- [ ] Health check endpoint `GET /health`

---

### Phase 2 — Sensor Simulator + Spot Detection (Week 2)

**Sensor Simulator**
- [ ] Seed 18 spots across 3 rows (A1–A6, B1–B6, C1–C6) with lat/lng coordinates
- [ ] Background job: every 2 seconds, randomly select a spot and flip its state
- [ ] Publish `spot.occupied` / `spot.freed` to Kafka topic `spot-events`
- [ ] Expose `POST /simulate/spot/:id/occupy` and `/free` for manual control
- [ ] Expose `POST /simulate/auto/start` and `/stop`

**Spot Detection Service**
- [ ] Set up Kafka consumer on `spot-events` topic
- [ ] Persist state change to `spots` and `spot_events` tables
- [ ] Publish normalised `spot.state.changed` event to `spot-state` topic
- [ ] Expose `GET /spots` and `GET /spots/:id` for admin queries

---

### Phase 3 — Availability Service (Week 2–3)

- [ ] Subscribe to `spot-state` Kafka topic
- [ ] On each event, update Redis hash: `spot:{id}:state = free|occupied|reserved`
- [ ] Implement `GET /spots/available?lat=&lng=&radius=` using PostGIS `ST_DWithin`
- [ ] Set up WebSocket server; broadcast `spot.state.changed` to all connected clients
- [ ] Cache full availability map in Redis with 5-second TTL for burst reads

---

### Phase 4 — Reservation Service (Week 3–4)

- [ ] Implement `POST /reservations`:
  1. Check availability via Availability Service
  2. Acquire Redis distributed lock on `lock:spot:{spotId}` with 10s TTL
  3. Create reservation with status `pending`
  4. Release lock after write commits
  5. Publish `reservation.created` event
- [ ] Implement `POST /reservations/:id/cancel`
- [ ] Implement `POST /reservations/:id/checkin` and `/checkout`
- [ ] Scheduled job (every 1 min): find `pending` reservations past `reserved_until`, set status `expired`, publish `reservation.expired`
- [ ] Consume `payment.success` → update reservation to `active`
- [ ] Consume `payment.failed` → update reservation to `cancelled`

---

### Phase 5 — Payment Service (Week 4)

- [ ] Integrate Stripe test mode (or build a mock provider)
- [ ] Consume `reservation.created` → create payment intent, hold amount
- [ ] Consume `reservation.completed` → charge actual duration amount
- [ ] Consume `reservation.cancelled` → issue full refund
- [ ] Implement `GET /payments/:reservationId` for receipt
- [ ] Publish `payment.success` / `payment.failed` / `payment.refunded`
- [ ] Idempotency key on all payment requests to prevent double charges

---

### Phase 6 — Penalty Service (Week 5)

- [ ] Consume `reservation.active` → start overstay timer in Redis (TTL = reserved duration)
- [ ] Scheduled job (every 2 min): check for spots still occupied after reservation window
- [ ] On overstay detected:
  - Tier 1 (0–15 min over): warning notification only
  - Tier 2 (15–60 min over): issue fine, publish `penalty.issued`
  - Tier 3 (60+ min over): escalate fine, flag account
- [ ] Implement `GET /penalties/user/:userId`
- [ ] Implement `POST /penalties/:id/pay`

---

### Phase 7 — Notification Service (Week 5–6)

- [ ] Set up Nodemailer (email) + Twilio test credentials (SMS)
- [ ] Subscribe to all relevant events and map to notification templates:

  | Event | Message |
  |---|---|
  | `reservation.created` | "Your spot A3 is reserved until 3:00 PM" |
  | `reservation.expiring` | "Your reservation expires in 5 minutes" |
  | `payment.success` | "Payment of $5.00 confirmed" |
  | `reservation.expired` | "Your reservation has expired" |
  | `penalty.issued` | "Overstay fine of $15.00 issued" |

- [ ] Queue notifications via an internal job queue (Bull/BullMQ) to avoid blocking consumers

---

### Phase 8 — Analytics Service (Week 6)

- [ ] Subscribe to all domain event topics
- [ ] Persist events to a time-series analytics table
- [ ] Compute and expose:
  - `GET /analytics/occupancy?lotId=&date=` — hourly occupancy rate
  - `GET /analytics/revenue?period=week|month` — revenue summary
  - `GET /analytics/violations` — violation counts by zone
- [ ] (Optional) Connect to a Grafana dashboard

---

### Phase 9 — Monitoring + Deployment (Week 7)

- [ ] Add Prometheus metrics endpoint `/metrics` to each service
- [ ] Set up Grafana dashboard for occupancy, request rates, event lag
- [ ] Write `docker-compose.yml` spinning up all services + Kafka + Redis + PostgreSQL
- [ ] Write a `README.md` with setup instructions and architecture diagram
- [ ] (Optional) Write Kubernetes manifests for each service

---

## 9. API Endpoints Reference

### Auth
```
POST   /auth/register
POST   /auth/login
POST   /auth/refresh
```

### Availability
```
GET    /spots/available?lat=&lng=&radius=
GET    /spots/:id
WS     /ws/availability   (real-time spot updates)
```

### Reservation
```
POST   /reservations
GET    /reservations/:id
POST   /reservations/:id/cancel
POST   /reservations/:id/checkin
POST   /reservations/:id/checkout
GET    /reservations/user/:userId
```

### Payment
```
GET    /payments/:reservationId
POST   /payments/:id/refund
```

### Penalty
```
GET    /penalties/user/:userId
POST   /penalties/:id/pay
```

### Simulator (admin)
```
POST   /simulate/spot/:id/occupy
POST   /simulate/spot/:id/free
POST   /simulate/auto/start
POST   /simulate/auto/stop
GET    /simulate/spots
```

---

## 10. Simulation Strategy

No physical hardware is needed. The Sensor Simulator Service acts as the ingestion adapter layer — the same role a real sensor gateway would play in production.

**Three simulation modes available:**

**Mode 1 — Auto (random):** Background job flips random spots every 1–2 seconds. Good for load testing and demos.

**Mode 2 — Manual REST:** Call `POST /simulate/spot/:id/occupy` during a live demo or interview. Full control over what changes and when.

**Mode 3 — Simulation Dashboard UI:** A web UI showing the parking grid. Click spots to toggle state. Visually impressive in demos.

**How to explain this in interviews:**
> "In production, a sensor adapter translates hardware signals into domain events. My simulator service is that adapter. Every downstream service — availability, reservation, penalty — consumes the exact same events regardless of whether the source is a real sensor or the simulator. The abstraction is the point."

---

## 11. Inter-service Communication Patterns

### Synchronous (REST)
Used when an immediate response is required.
- Driver app → API Gateway → Reservation Service (create reservation)
- Reservation Service → Availability Service (check spot before locking)

### Asynchronous (Kafka events)
Used for non-blocking flows and decoupling.
- Spot state changes, reservation lifecycle, payment outcomes, penalties
- Any service that needs to react to another service's state change

### Distributed Lock (Redis)
Used to prevent race conditions on reservation.
```
SETNX lock:spot:{spotId} {reservationId}  EX 10
```
- Acquired before creating reservation
- Released after DB write commits
- If payment fails within TTL, lock is released and spot freed

### Saga Pattern (Reservation + Payment)
```
1. Reservation created (status: pending)
2. reservation.created event published
3. Payment Service attempts charge
4. If payment.success → Reservation set to active
5. If payment.failed → Reservation cancelled, spot released
```
No two-phase commit — compensating transactions handle rollback.

---

## 12. Folder Structure

```
smart-parking/
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   ├── spot-detection-service/
│   ├── availability-service/
│   ├── reservation-service/
│   ├── payment-service/
│   ├── penalty-service/
│   ├── notification-service/
│   ├── analytics-service/
│   └── sensor-simulator/
├── shared/
│   ├── kafka/           # Shared Kafka client config
│   ├── events/          # Event type definitions (TypeScript interfaces)
│   └── middleware/      # JWT validation, error handler
├── infra/
│   ├── docker-compose.yml
│   ├── kafka/
│   ├── postgres/        # Init scripts per service DB
│   └── grafana/
├── docs/
│   ├── architecture.md
│   └── api-reference.md
└── README.md
```

Each service follows this internal structure:
```
service-name/
├── src/
│   ├── routes/
│   ├── controllers/
│   ├── services/
│   ├── repositories/
│   ├── models/
│   ├── events/          # Kafka producers & consumers
│   └── jobs/            # Scheduled jobs
├── tests/
├── Dockerfile
└── package.json
```

---

## 13. Testing Strategy

### Unit Tests (per service)
- Business logic: lock acquisition, expiry calculation, penalty tier logic
- Event payload validation
- Tools: Jest (Node) or JUnit (Java)

### Integration Tests
- Service + database: reservation creation, payment flow
- Service + Kafka: event published and consumed correctly
- Tools: Jest + Testcontainers (spins up real Postgres/Kafka in Docker)

### End-to-End Tests
- Full reservation flow: find spot → reserve → pay → check in → check out
- Overstay flow: reserve → exceed window → penalty issued → notification sent
- Tools: Supertest or Postman/Newman

### Load Tests (optional but impressive)
- Simulate 100 concurrent reservation requests for the same spot
- Verify only 1 succeeds; rest get `409 Conflict`
- Tools: k6 or Artillery

---

## 14. Deployment Plan

### Local (Docker Compose)
```bash
docker-compose up --build
```
Spins up: all 9 services + Kafka + Zookeeper + Redis + PostgreSQL (per service) + Grafana

### Cloud (optional, Phase 9)
- Push images to Docker Hub or AWS ECR
- Deploy to AWS ECS (simple) or EKS (Kubernetes, impressive)
- Use managed Kafka (AWS MSK or Confluent Cloud)
- Use managed PostgreSQL (AWS RDS)
- Use managed Redis (AWS ElastiCache)

### Environment Variables (each service)
```env
PORT=
DATABASE_URL=
REDIS_URL=
KAFKA_BROKER=
JWT_SECRET=
STRIPE_SECRET_KEY=
```

---

## 15. Interview Talking Points

These are the questions you should be ready to answer about this project:

**"Why microservices over a monolith?"**
> Each service has a clear bounded context. Spot detection, reservation, and payment have very different scaling needs and failure modes. A monolith couples them — a payment spike would affect spot detection.

**"How do you prevent two users from booking the same spot?"**
> Redis distributed lock with SETNX and a TTL. Only the first requester gets the lock; subsequent requests get a 409. If payment fails within the TTL, the lock expires and the spot is freed automatically.

**"What happens if the Payment Service goes down mid-reservation?"**
> The saga pattern. The reservation stays in `pending` state. When payment recovers, it retries. If it cannot recover, a compensating transaction cancels the reservation and releases the spot. No two-phase commit needed.

**"How do you keep availability data fresh without hammering the database?"**
> Availability state lives in Redis, updated by Kafka consumers in near real-time. The availability query hits Redis, not Postgres. Postgres is the source of truth; Redis is the read cache. TTL is set as a fallback.

**"How does the simulator replace real hardware?"**
> The simulator is an ingestion adapter — the same role a real sensor gateway would play. It publishes identical events to the same Kafka topic. Every downstream service is completely decoupled from the source. Swapping the simulator for real hardware means replacing one service, zero changes elsewhere.

**"How do you find spots near a driver's location?"**
> PostGIS `ST_DWithin` query on the spots table, filtering by geography point and radius in metres. The result is intersected with the Redis availability cache to return only free spots within range.

---

## Phase 10 — Driver Mobile App (Flutter)

### Overview

A cross-platform Flutter application for drivers to find nearby parking spots, make reservations, pay, check in/out, and receive real-time alerts. All data comes from the existing Spring Boot backend via the API Gateway at `http://localhost:8080`.

### Tech Stack

| Concern | Library / Tool | Why |
|---|---|---|
| Framework | Flutter 3.x (Dart) | Single codebase for iOS + Android |
| State Management | Riverpod 2 | Compile-safe, testable, no boilerplate |
| HTTP Client | Dio + dio_interceptors | Interceptor for JWT attach + refresh |
| Maps | flutter_map + OpenStreetMap | No billing key required |
| WebSocket | web_socket_channel | Real-time spot updates |
| Secure Storage | flutter_secure_storage | JWT tokens (Keychain / Keystore) |
| Local Notifications | flutter_local_notifications | Overstay warning, reservation expiry |
| Navigation | go_router | Declarative, deep-link ready |
| Forms | reactive_forms | Validation without manual controllers |
| Date/Time | intl | Formatting |
| Testing | flutter_test + Mockito | Unit + widget tests |

### Project Structure

```
mobile/
├── lib/
│   ├── main.dart
│   ├── app.dart
│   ├── core/
│   │   ├── api/
│   │   │   ├── api_client.dart
│   │   │   ├── auth_interceptor.dart
│   │   │   └── endpoints.dart
│   │   ├── auth/
│   │   │   ├── token_storage.dart
│   │   │   └── auth_notifier.dart
│   │   ├── websocket/
│   │   │   └── availability_socket.dart
│   │   └── router/
│   │       └── app_router.dart
│   ├── features/
│   │   ├── auth/
│   │   ├── map/
│   │   ├── reservation/
│   │   ├── payment/
│   │   ├── penalty/
│   │   ├── notification/
│   │   ├── vehicle/
│   │   └── profile/
│   └── shared/
│       ├── widgets/
│       └── theme/
├── test/
├── pubspec.yaml
└── README.md
```

### Screens & Navigation

```
/login                  LoginScreen
/register               RegisterScreen
/home                   MapScreen (bottom nav shell)
  /home/map             MapScreen tab
  /home/reservations    MyReservationsScreen tab
  /home/notifications   NotificationsScreen tab (unread badge)
  /home/profile         ProfileScreen tab
/reservation/new        ReservationFormScreen
/reservation/:id        ReservationDetailScreen
/payment/:reservationId PaymentReceiptScreen
/penalties              MyPenaltiesScreen
/vehicles               VehiclesScreen
```

### Implementation Phases

| Phase | Deliverable | Depends on |
|---|---|---|
| A | Auth (login, register, token storage) | — |
| B | Spot map with real-time WebSocket updates | Phase A |
| C | Reservation form + detail + list | Phase B |
| D | Payment receipt screen | Phase C |
| E | In-app notifications + local push | Phase C |
| F | Penalties list + pay | Phase E |
| G | Profile + vehicle management | Phase A |

### Phase A — Auth

**Screens:** `LoginScreen`, `RegisterScreen`

**API calls:**
```
POST /auth/register    { name, email, password, phone }
POST /auth/login       { email, password }
POST /auth/refresh     { refreshToken }
```

- `AuthRepository` wraps Dio calls to `/auth/*`
- `AuthNotifier` (Riverpod `AsyncNotifier`) holds `UserModel?`
- `TokenStorage` stores `accessToken` + `refreshToken` in `flutter_secure_storage`
- `AuthInterceptor` on Dio: attaches `Authorization: Bearer <token>` to every request; on 401, calls `/auth/refresh` once and retries; on second 401, clears tokens and redirects to login
- `GoRouter` redirect: unauthenticated users always land on `/login`

### Phase B — Spot Map

**Screen:** `MapScreen` + `SpotBottomSheet`

**API calls:**
```
GET /spots/available?lat=&lng=&radius=500
WS  /ws/availability
```

- `flutter_map` with OpenStreetMap tiles
- Green = free, amber = reserved, red = occupied
- WebSocket updates spot state without re-fetch
- Tap spot → `SpotBottomSheet` → Reserve button → `ReservationFormScreen`

### Phase C — Reservation Flow

**Screens:** `ReservationFormScreen`, `ReservationDetailScreen`, `MyReservationsScreen`

- Vehicle picker, date/time pickers, estimated cost display
- Polls `GET /reservations/{id}` every 5s while status is `PENDING`
- Check In / Check Out / Cancel buttons based on status

### Phase D — Payment Receipt

- `GET /payments/{reservationId}` — shows amount, duration, reference
- Optional PDF generation via `pdf` package

### Phase E — Notifications

- Unread badge polling every 30s
- Tap notification → navigate to relevant screen
- Local push via `flutter_local_notifications` for penalty warnings

### Phase F — Penalties

- Tier badges: WARNING (grey) / FINE (orange) / ESCALATED (red)
- Pay Now with `AlertDialog` confirmation

### Phase G — Profile & Vehicles

- Display user info + logout
- Manage saved vehicle plates

### JWT Token Flow

```
App start → TokenStorage.read()
  ├─ tokens exist → authenticated
  └─ no tokens    → /login

Every request: AuthInterceptor attaches Bearer token
  └─ 401 → POST /auth/refresh → retry
        └─ 401 again → clear tokens → /login
```

### Error Handling

ProblemDetail errors from backend parsed to `AppException { status, title, detail }` and shown via `ErrorBanner` widget.

### Testing Strategy

| Layer | Tool | What to test |
|---|---|---|
| Repository | mockito + flutter_test | HTTP responses + error cases |
| Notifiers | flutter_test | State transitions |
| Widgets | WidgetTester | Form validation, loading states, badge counts |
| Integration | integration_test | Full login → reserve → checkin → checkout |

### Setup

```bash
cd mobile
flutter pub get
flutter run -d android    # Android emulator
flutter run -d ios        # iOS simulator
flutter test
flutter build apk --release
```

### Environment Configuration

```dart
// lib/core/api/endpoints.dart
const String kBaseUrl = String.fromEnvironment(
  'BASE_URL',
  defaultValue: 'http://10.0.2.2:8080',  // Android emulator → localhost
);
const String kWsUrl = String.fromEnvironment(
  'WS_URL',
  defaultValue: 'ws://10.0.2.2:8080/ws/availability',
);
```

> Android emulator uses `10.0.2.2` to reach the host machine's `localhost`. iOS simulator uses `localhost` directly.

---

## Phase 11 — Operator Web Dashboard (React)

### Overview

A React + TypeScript single-page application for parking lot operators. Monitors live occupancy, tracks revenue, manages violations, and shows real-time spot state — all from the existing analytics API.

### Tech Stack

| Concern | Library / Tool | Why |
|---|---|---|
| Framework | React 18 + TypeScript | Industry standard; strong typing |
| Build Tool | Vite | Fast dev server + HMR |
| Styling | Tailwind CSS + shadcn/ui | Utility-first; pre-built accessible components |
| State / Server Cache | TanStack Query v5 | Auto-refetch, caching, loading/error states |
| Auth State | Zustand | Lightweight client state for token + user |
| Charts | Recharts | Composable, works well with React |
| Maps | react-leaflet + OpenStreetMap | Spot occupancy map |
| WebSocket | native browser WebSocket | Real-time spot updates |
| Forms | React Hook Form + Zod | Validation with TypeScript inference |
| HTTP | Axios | Interceptors for JWT attach + refresh |
| Routing | React Router v6 | Standard SPA routing |
| Testing | Vitest + React Testing Library | Fast, Jest-compatible |

### Project Structure

```
dashboard/
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── lib/
│   │   ├── axios.ts
│   │   ├── queryClient.ts
│   │   └── websocket.ts
│   ├── store/
│   │   └── authStore.ts
│   ├── types/
│   │   ├── api.ts
│   │   └── events.ts
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useOccupancy.ts
│   │   ├── useRevenue.ts
│   │   ├── useViolations.ts
│   │   └── useSpotUpdates.ts
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── OccupancyPage.tsx
│   │   ├── RevenuePage.tsx
│   │   ├── ViolationsPage.tsx
│   │   └── EventsPage.tsx
│   └── components/
│       ├── layout/
│       ├── dashboard/
│       ├── occupancy/
│       ├── revenue/
│       ├── violations/
│       └── shared/
├── vite.config.ts
├── tailwind.config.ts
└── package.json
```

### Pages & Routing

```
/login        LoginPage          (public)
/             DashboardPage      (KPI cards + mini charts)
/occupancy    OccupancyPage      (live map + date filter)
/revenue      RevenuePage        (line chart + week|month toggle)
/violations   ViolationsPage     (table + tier pie chart)
/events       EventsPage         (raw event log + type filter)
```

### Dashboard Layout

```
┌────────────────────────────────────────────────┐
│  KPI Cards: Net Revenue | Reservations | Violations | Uptime  │
├───────────────────────┬────────────────────────┤
│  Revenue (week)       │  Violation Pie Chart   │
│  [RevenueLineChart]   │  T1:grey T2:orange T3:red │
├───────────────────────┴────────────────────────┤
│  Today's Hourly Occupancy [HourlyBarChart]      │
├────────────────────────────────────────────────┤
│  Recent Events (last 10) [EventsTable mini]     │
└────────────────────────────────────────────────┘
```

### API Hooks (TanStack Query)

```ts
useRevenue('week')    → GET /analytics/revenue?period=week    refetchInterval: 60s
useOccupancy(date)    → GET /analytics/occupancy?date=        refetchInterval: 30s
useViolations()       → GET /analytics/violations             refetchInterval: 60s
```

### Implementation Phases

| Phase | Deliverable |
|---|---|
| A | Project setup, routing, auth, Axios + JWT refresh |
| B | Dashboard overview — KPI cards + charts |
| C | Occupancy page — live map + WebSocket |
| D | Revenue page — line chart + period toggle |
| E | Violations page — pie chart + table |
| F | Events page — raw log with type filter |
| G | Polish — responsive layout, loading skeletons, error boundaries |

### Setup

```bash
cd dashboard
npm install
npm run dev         # Dev server at http://localhost:5173
npm run test
npm run build
```

**.env.local:**
```env
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws/availability
```

---

*Built as a portfolio project demonstrating: event-driven microservices, distributed locking, saga pattern, geospatial queries, real-time WebSocket push, and scheduled enforcement jobs.*
