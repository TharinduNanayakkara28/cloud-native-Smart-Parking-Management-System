# Driver Mobile App — Full Implementation Plan

## Overview

A cross-platform Flutter application for drivers to find nearby parking spots, make reservations, pay, check in/out, and receive real-time alerts. All data comes from the existing Spring Boot backend via the API Gateway at `http://localhost:8080`.

---

## Tech Stack

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

---

## Project Structure

```
mobile/
├── lib/
│   ├── main.dart
│   ├── app.dart                        # MaterialApp + GoRouter setup
│   ├── core/
│   │   ├── api/
│   │   │   ├── api_client.dart         # Dio instance + interceptors
│   │   │   ├── auth_interceptor.dart   # Attach Bearer token
│   │   │   └── endpoints.dart          # All URL constants
│   │   ├── auth/
│   │   │   ├── token_storage.dart      # flutter_secure_storage wrapper
│   │   │   └── auth_notifier.dart      # Riverpod: login state
│   │   ├── websocket/
│   │   │   └── availability_socket.dart
│   │   └── router/
│   │       └── app_router.dart         # go_router routes
│   ├── features/
│   │   ├── auth/
│   │   │   ├── data/
│   │   │   │   └── auth_repository.dart
│   │   │   ├── domain/
│   │   │   │   └── user_model.dart
│   │   │   └── presentation/
│   │   │       ├── login_screen.dart
│   │   │       └── register_screen.dart
│   │   ├── map/
│   │   │   ├── data/
│   │   │   │   └── spot_repository.dart
│   │   │   ├── domain/
│   │   │   │   └── spot_model.dart
│   │   │   └── presentation/
│   │   │       ├── map_screen.dart
│   │   │       └── spot_bottom_sheet.dart
│   │   ├── reservation/
│   │   │   ├── data/
│   │   │   │   └── reservation_repository.dart
│   │   │   ├── domain/
│   │   │   │   └── reservation_model.dart
│   │   │   └── presentation/
│   │   │       ├── reservation_form_screen.dart
│   │   │       ├── reservation_detail_screen.dart
│   │   │       └── my_reservations_screen.dart
│   │   ├── payment/
│   │   │   ├── data/
│   │   │   │   └── payment_repository.dart
│   │   │   └── presentation/
│   │   │       └── payment_receipt_screen.dart
│   │   ├── penalty/
│   │   │   ├── data/
│   │   │   │   └── penalty_repository.dart
│   │   │   └── presentation/
│   │   │       └── my_penalties_screen.dart
│   │   ├── notification/
│   │   │   ├── data/
│   │   │   │   └── notification_repository.dart
│   │   │   └── presentation/
│   │   │       └── notifications_screen.dart
│   │   ├── vehicle/
│   │   │   ├── data/
│   │   │   │   └── vehicle_repository.dart
│   │   │   └── presentation/
│   │   │       └── vehicles_screen.dart
│   │   └── profile/
│   │       └── presentation/
│   │           └── profile_screen.dart
│   └── shared/
│       ├── widgets/
│       │   ├── error_banner.dart
│       │   ├── loading_button.dart
│       │   └── status_badge.dart
│       └── theme/
│           └── app_theme.dart
├── test/
│   ├── unit/
│   └── widget/
├── pubspec.yaml
└── README.md
```

---

## Screens & Navigation

```
/login                  LoginScreen
/register               RegisterScreen
/home                   MapScreen (bottom nav shell)
  /home/map             MapScreen tab — spot map + real-time updates
  /home/reservations    MyReservationsScreen tab
  /home/notifications   NotificationsScreen tab  (unread badge)
  /home/profile         ProfileScreen tab
/reservation/new        ReservationFormScreen    ← from spot bottom sheet
/reservation/:id        ReservationDetailScreen  ← check in / check out
/payment/:reservationId PaymentReceiptScreen
/penalties              MyPenaltiesScreen
/vehicles               VehiclesScreen
```

---

## Feature Breakdown

### Phase A — Auth

**Screens:** `LoginScreen`, `RegisterScreen`

**API calls:**
```
POST /auth/register    { name, email, password, phone }
POST /auth/login       { email, password }
POST /auth/refresh     { refreshToken }
```

**Implementation:**
- `AuthRepository` wraps Dio calls to `/auth/*`
- `AuthNotifier` (Riverpod `AsyncNotifier`) holds `UserModel?`
- `TokenStorage` stores `accessToken` + `refreshToken` in `flutter_secure_storage`
- `AuthInterceptor` on Dio: attaches `Authorization: Bearer <token>` to every request; on 401, calls `/auth/refresh` once and retries; on second 401, clears tokens and redirects to login
- `GoRouter` redirect: unauthenticated users always land on `/login`

**Key models:**
```dart
class UserModel {
  final String id;
  final String name;
  final String email;
  final String? phone;
}
```

---

### Phase B — Spot Map (Core Feature)

**Screen:** `MapScreen` + `SpotBottomSheet`

**API calls:**
```
GET /spots/available?lat=&lng=&radius=500
WS  /ws/availability    (STOMP over WebSocket)
```

**Implementation:**
- `flutter_map` with OpenStreetMap tiles renders the parking grid
- On map load: `GET /spots/available` with device GPS coordinates
- Each available spot = green circle marker; occupied = red; reserved = amber
- `AvailabilitySocket` subscribes to `/topic/spots` via WebSocket; on `spot.state.changed` event, updates the local spot state in a Riverpod `StateProvider` without full re-fetch
- Tap a green spot → `SpotBottomSheet` slides up showing spot number, floor, and a **Reserve** button
- Reserve button navigates to `ReservationFormScreen` with the spotId pre-filled

**Key state:**
```dart
final spotsProvider = StateNotifierProvider<SpotsNotifier, AsyncValue<List<SpotModel>>>(...);
```

---

### Phase C — Reservation Flow

**Screens:** `ReservationFormScreen`, `ReservationDetailScreen`, `MyReservationsScreen`

**API calls:**
```
POST /reservations                         { spotId, vehiclePlate, reservedFrom, reservedUntil }
GET  /reservations/{id}
GET  /reservations/user/me
POST /reservations/{id}/cancel
POST /reservations/{id}/checkin
POST /reservations/{id}/checkout
```

**ReservationFormScreen:**
- Vehicle plate picker (from user's saved vehicles or free-text)
- Date/time pickers for `reservedFrom` and `reservedUntil`
- Shows estimated cost (calculated client-side: hours × hourly rate from config)
- On submit: `POST /reservations` → polls or shows loading until payment saga completes
- On `ACTIVE` status: navigate to `ReservationDetailScreen`

**ReservationDetailScreen:**
- Shows status badge: `PENDING` / `ACTIVE` / `COMPLETED` / `EXPIRED` / `CANCELLED`
- `PENDING` state: shows spinner ("Awaiting payment confirmation...")
- `ACTIVE` state: shows **Check In** and **Cancel** buttons; countdown timer to `reservedUntil`
- Checked-in state: shows **Check Out** button; shows elapsed time
- `COMPLETED` state: shows **View Receipt** button → `PaymentReceiptScreen`
- Polls `GET /reservations/{id}` every 5 seconds while status is `PENDING`

**MyReservationsScreen:**
- Lists all reservations from `GET /reservations/user/me`
- Filter tabs: Active | Upcoming | Past
- Tap any row → `ReservationDetailScreen`

---

### Phase D — Payment Receipt

**Screen:** `PaymentReceiptScreen`

**API calls:**
```
GET /payments/{reservationId}
```

**Implementation:**
- Displays: reservation dates, duration, amount charged, payment reference
- **Download PDF** button (optional: use `pdf` package to generate a receipt PDF)
- Shows refund details if status is `REFUNDED`

---

### Phase E — Notifications

**Screen:** `NotificationsScreen`

**API calls:**
```
GET  /notifications/user/me
GET  /notifications/user/me/unread-count
POST /notifications/{id}/read
POST /notifications/user/me/read-all
```

**Implementation:**
- Pull-to-refresh list, newest first
- Unread items have a coloured left border
- Tap item → marks as read (`POST /notifications/{id}/read`) + navigates to relevant screen (e.g., tap a `RESERVATION_ACTIVE` notification → open that reservation)
- `NotificationsIconButton` in app bar: shows red badge with unread count, polls every 30 seconds
- Local push notification via `flutter_local_notifications` for overstay warnings (triggered when the app receives a `PENALTY_ISSUED` notification from the notification API)

---

### Phase F — Penalties

**Screen:** `MyPenaltiesScreen`

**API calls:**
```
GET  /penalties/user/me
POST /penalties/{id}/pay
```

**Implementation:**
- List of issued penalties with tier badge: WARNING (grey) / FINE (orange) / ESCALATED (red)
- `ISSUED` penalties show a **Pay Now** button; `PAID` show a green tick
- Pay confirms with an `AlertDialog` before calling `POST /penalties/{id}/pay`

---

### Phase G — Profile & Vehicles

**Screens:** `ProfileScreen`, `VehiclesScreen`

**API calls:**
```
GET  /users/me
POST /users/me/vehicles    { plate, make, model }
GET  /users/me/vehicles
```

**Implementation:**
- `ProfileScreen`: displays name, email, phone; logout button (clears tokens, redirects to login)
- `VehiclesScreen`: list of registered vehicles + FAB to add new vehicle

---

## Real-Time Spot Updates (WebSocket)

```dart
class AvailabilitySocket {
  final WebSocketChannel _channel;

  void connect(String url) {
    _channel = WebSocketChannel.connect(Uri.parse('ws://localhost:8080/ws/availability'));
    _channel.stream.listen((message) {
      final event = jsonDecode(message);
      if (event['eventType'] == 'spot.state.changed') {
        _updateSpot(event['payload']);
      }
    });
  }

  void dispose() => _channel.sink.close();
}
```

The `AvailabilitySocket` is managed in a Riverpod provider that connects on app foreground and disconnects on background.

---

## JWT Token Flow

```
App start
  └─► TokenStorage.read()
        ├─ tokens exist → auth state = authenticated
        └─ no tokens    → navigate to /login

Every API request (AuthInterceptor)
  └─► Attach: Authorization: Bearer <accessToken>
        └─ 401 received
              └─► POST /auth/refresh
                    ├─ success → retry original request with new token
                    └─ 401 again → clear tokens → navigate to /login
```

---

## Error Handling

All errors come as ProblemDetail JSON from the backend:
```json
{ "status": 409, "title": "Conflict", "detail": "Spot is not available" }
```

`ApiClient` catches `DioException`, reads `detail` from the error body, and throws typed `AppException` objects:
```dart
class AppException {
  final int status;
  final String title;
  final String detail;
}
```

Each screen catches `AppException` and shows an `ErrorBanner` widget at the top of the screen.

---

## Testing Strategy

| Layer | Tool | What to test |
|---|---|---|
| Repository | `mockito` + `flutter_test` | HTTP responses + error cases |
| Notifiers | `flutter_test` | State transitions (login, reservation status changes) |
| Widgets | `flutter_test` + `WidgetTester` | Form validation, loading states, badge counts |
| Integration | `integration_test` | Full login → reserve → checkin → checkout flow on emulator |

---

## Implementation Phases

| Phase | Deliverable | Depends on |
|---|---|---|
| A | Auth (login, register, token storage) | — |
| B | Spot map with real-time WebSocket updates | Phase A |
| C | Reservation form + detail + list | Phase B |
| D | Payment receipt screen | Phase C |
| E | In-app notifications + local push | Phase C |
| F | Penalties list + pay | Phase E |
| G | Profile + vehicle management | Phase A |

---

## Setup

```bash
# Prerequisites: Flutter 3.x, Android Studio or Xcode

cd mobile
flutter pub get

# Run on Android emulator
flutter run -d android

# Run on iOS simulator
flutter run -d ios

# Run tests
flutter test

# Build release APK
flutter build apk --release
```

---

## Environment Configuration

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

> **Note:** Android emulator uses `10.0.2.2` to reach the host machine's `localhost`. iOS simulator uses `localhost` directly.
