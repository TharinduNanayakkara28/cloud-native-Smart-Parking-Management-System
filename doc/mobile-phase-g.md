# Mobile Phase G — Profile & Vehicles

## What Was Built

User profile screen showing account info and navigation to sub-features, plus a vehicle management screen with full-screen list and a modal bottom sheet to add new vehicles.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/vehicle/domain/vehicle_model.dart` | `VehicleModel {id, plate, make, model, isDefault}`; `displayName` computed property (make + model, falls back to plate) |
| `mobile/lib/features/vehicle/data/vehicle_repository.dart` | `getMyVehicles()` + `addVehicle({plate, make?, model?})`; `myVehiclesProvider` FutureProvider |
| `mobile/lib/features/vehicle/presentation/vehicles_screen.dart` | Card list of vehicles; FAB → `_AddVehicleSheet` modal bottom sheet with plate/make/model fields; empty state with inline Add button |
| `mobile/lib/features/profile/presentation/profile_screen.dart` | Initials avatar, name/email/phone display, nav cards to Vehicles and Penalties, logout with confirmation dialog |
| `mobile/test/unit/vehicle_repository_test.dart` | 10 tests — list/empty/401, add 201/409, model parsing, isDefault false, empty make/model, displayName logic |

## Files Updated

| File | Change |
|---|---|
| `mobile/lib/core/router/app_router.dart` | `/home/profile` → `ProfileScreen()`; `/vehicles` route added → `VehiclesScreen()` |

---

## Key Design Decisions

**Profile reads from `authNotifierProvider`**  
`ProfileScreen` watches `authNotifierProvider.valueOrNull` — no separate API call needed. The user data was already loaded at startup by `AuthNotifier.build()`. This avoids an extra `GET /users/me` on every profile tab visit.

**Logout guard dialog**  
`_confirmLogout()` shows an `AlertDialog` before calling `authNotifier.logout()`. Logout clears tokens from `flutter_secure_storage` and sets state to `AsyncData(null)`, which triggers `_RouterNotifier` to redirect to `/login` via `notifyListeners()`.

**`_AddVehicleSheet` uses `ConsumerStatefulWidget`**  
The modal bottom sheet manages its own `TextEditingController` instances and submitting state. `MediaQuery.viewInsetsOf(context).bottom` is added to `Padding.bottom` so the form scrolls above the keyboard. The sheet pops itself on success and calls `onAdded()` (which invalidates `myVehiclesProvider`).

**`displayName` computed property**  
`VehicleModel.displayName` returns "Toyota Camry" when both make and model are set, just "Toyota" when only make is set, and falls back to the plate string otherwise. This avoids null-check repetition in the UI.

**No delete vehicle in Phase G**  
The plan specifies only `GET` and `POST` for vehicles. Deletion is left for a future phase to avoid adding endpoints not in the backend spec.

---

## Navigation Flow

```
/home/profile (ProfileScreen)
    ├── context.push('/vehicles')  → VehiclesScreen
    │       └── FAB → _AddVehicleSheet (modal)
    │               └── POST /users/me/vehicles
    └── context.push('/penalties') → MyPenaltiesScreen (Phase F)
```

---

## API Endpoints Used

```
GET  /users/me             (via authNotifierProvider — no extra call)
GET  /users/me/vehicles
POST /users/me/vehicles    { plate, make?, model? }
```

---

## Profile Screen Layout

```
┌─────────────────────────────────────┐
│             [TL]                    │   large CircleAvatar with initials
│          Tharindu L.                │
│       user@example.com              │
│         +1 234 567 8900             │
├─────────────────────────────────────┤
│  🚗  My Vehicles          >         │   navigates to /vehicles
│  ──────────────────────────         │
│  ⚠️  My Penalties         >         │   navigates to /penalties
├─────────────────────────────────────┤
│  ℹ️  Account ID                     │
│     3f8a-...                        │
├─────────────────────────────────────┤
│    [Log Out]                        │   error-coloured OutlinedButton
└─────────────────────────────────────┘
```

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/vehicle_repository_test.dart
```

In the app:
1. Log in → tap Profile tab
2. Tap **My Vehicles** → empty state shows
3. Tap **Add Vehicle** → enter plate `ABC-001`, make `Toyota`, model `Camry` → tap Add
4. Vehicle appears in the list
5. Back to Profile → tap **Log Out** → confirm → redirected to Login
