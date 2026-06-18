# Mobile Phase B — Spot Map

## What Was Built

Live interactive parking map showing real-time spot availability. Uses `flutter_map` for rendering, the device GPS for the initial camera centre, and a WebSocket stream that patches spot colours in-memory as sensor events arrive — without re-fetching from the REST API.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/map/domain/spot_model.dart` | `SpotModel {id, spotNumber, floor, latLng, state}`, `SpotState` enum (free/reserved/occupied), `SpotStateEvent` parsed from WebSocket payload |
| `mobile/lib/features/map/data/spot_repository.dart` | `GET /spots/available?lat=&lng=&radius=` → `List<SpotModel>`; wraps DioException → AppException |
| `mobile/lib/features/map/data/spots_notifier.dart` | `userLocationProvider` (geolocator, falls back to default coords if denied) + `SpotsNotifier` AsyncNotifier (loads spots, applies WebSocket patches, `refresh()`) |
| `mobile/lib/core/websocket/availability_socket.dart` | `StreamProvider.autoDispose<SpotStateEvent>` — connects to `/ws/availability`, parses `spot.state.changed` events, auto-reconnects after 3 s on disconnect |
| `mobile/lib/features/map/presentation/map_screen.dart` | `flutter_map` + OSM tiles; colour-coded circle markers; loading overlay; error banner; map legend; refresh button in AppBar |
| `mobile/lib/features/map/presentation/spot_bottom_sheet.dart` | Modal bottom sheet on marker tap — shows spot number, floor, state chip; **Reserve This Spot** navigates to `/reservation/new`; button disabled when not free |
| `mobile/lib/shared/widgets/status_badge.dart` | `StatusBadge(label, variant)` — reusable coloured chip for success/warning/error/info/neutral; used by Phases C–F |
| `mobile/test/unit/spot_repository_test.dart` | 8 tests — parse list, empty list, correct query params, 503 AppException, all three SpotState values, null floor, copyWith |

## Files Updated

| File | Change |
|---|---|
| `mobile/pubspec.yaml` | Added `geolocator: ^12.0.0` |
| `mobile/lib/core/router/app_router.dart` | `/home/map` now routes to `MapScreen`; `/reservation/new` and `/reservation/:id` added as placeholder routes |

---

## Key Design Decisions

**In-memory WebSocket patching**  
After the initial `GET /spots/available`, the `SpotsNotifier` subscribes to `availabilitySocketProvider`. On each `SpotStateEvent`, it maps over the current list and replaces only the affected spot's `state`. No network request is triggered. This makes the map feel live without hammering the API.

**Auto-reconnecting WebSocket**  
`availabilitySocketProvider` is an `async*` generator in an infinite loop. On any error or clean close, it waits 3 seconds then reconnects. When the last widget unsubscribes (`autoDispose`), the generator is cancelled and the socket closed.

**GPS graceful degradation**  
`userLocationProvider` requests `LocationPermission` at runtime. If the user denies or location services are off, it returns `null`. `SpotsNotifier.build()` falls back to the hardcoded Jakarta city-centre coordinates that match the seeded spots. The map still works — it just centres elsewhere.

**Separate `_refreshDio` in the interceptor**  
The `availabilitySocketProvider` connects directly to the WebSocket URL — it bypasses Dio entirely, so no token is needed for the WS handshake. The backend's availability-service allows unauthenticated WebSocket connections for spot state broadcasts.

**SpotBottomSheet navigation**  
The Reserve button encodes both `spotId` (UUID) and `spotNumber` (human label) in the query string:  
`/reservation/new?spotId=uuid&spotNumber=A1`  
This gives the form screen everything it needs without a separate API call.

---

## API / WebSocket Used

```
GET /spots/available?lat=&lng=&radius=500
WS  /ws/availability    → receives { eventType: "spot.state.changed", payload: { spotId, state } }
```

---

## Spot Colour Coding

| Colour | Meaning |
|---|---|
| Green | FREE — can be reserved |
| Amber | RESERVED — spot has an active reservation |
| Red | OCCUPIED — vehicle detected by sensor |

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/spot_repository_test.dart
```

To see the map live, start the full backend stack:
```bash
docker compose -f infra/docker-compose.yml up --build
# Start the sensor simulator to see spots change colour in real time:
curl -X POST http://localhost:8080/simulate/auto/start
```

Then run the app on an emulator. The map opens at login and immediately loads spots. Spots update colour automatically as the simulator toggles them.
