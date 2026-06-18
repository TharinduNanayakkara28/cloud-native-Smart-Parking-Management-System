# Mobile Phase C — Reservation Flow

## What Was Built

End-to-end reservation lifecycle — creating a reservation, tracking the payment saga in real time, checking in and out, cancelling, and browsing all reservations by status.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/reservation/domain/reservation_model.dart` | `ReservationModel` with all fields; `ReservationStatus` enum (pending/active/completed/expired/cancelled); `isCheckedIn` computed property |
| `mobile/lib/features/reservation/data/reservation_repository.dart` | `create`, `getById`, `getMyReservations`, `cancel`, `checkIn`, `checkOut` — all wrap DioException → AppException; `myReservationsProvider` FutureProvider |
| `mobile/lib/features/reservation/presentation/reservation_form_screen.dart` | Date/time pickers, vehicle plate field, live estimated cost card, client-side validation, `POST /reservations` on submit → navigate to detail |
| `mobile/lib/features/reservation/presentation/reservation_detail_screen.dart` | Loads by ID; single `Timer.periodic(1s)` drives countdown display and API polling (3 s while PENDING, 30 s while ACTIVE); check-in/check-out/cancel action buttons; cancel shows confirmation dialog |
| `mobile/lib/features/reservation/presentation/my_reservations_screen.dart` | `DefaultTabController` with Active / Upcoming / Past tabs; pull-to-refresh via `ref.refresh`; each tile taps through to `ReservationDetailScreen` |
| `mobile/test/unit/reservation_repository_test.dart` | 11 tests — create 201/409, getById 200/404, getMyReservations list/empty, checkIn/checkOut/cancel, isCheckedIn logic |

## Files Updated

| File | Change |
|---|---|
| `mobile/lib/core/router/app_router.dart` | `/reservation/new` → `ReservationFormScreen`; `/reservation/:id` → `ReservationDetailScreen`; `/home/reservations` → `MyReservationsScreen`; `/payment/:reservationId` placeholder added for Phase D |

---

## Reservation State Machine

```
         POST /reservations
               │
               ▼
           PENDING  ◄── payment saga running (polls every 3 s)
               │
     payment.success │ payment.failed
               │              │
               ▼              ▼
           ACTIVE          (cancelled by saga)
          /      \
POST /cancel  POST /checkin
    │               │
    ▼               ▼
CANCELLED       ACTIVE (isCheckedIn=true)
                    │
            POST /checkout
                    │
                    ▼
               COMPLETED
                    │
          scheduler (if not checked in before reservedUntil)
                    │
                    ▼
               EXPIRED
```

---

## Key Design Decisions

**Single timer for countdown + polling**  
`ReservationDetailScreen` runs one `Timer.periodic(const Duration(seconds: 1))`. Every tick increments `_ticks` and calls `setState` to refresh the countdown display. Every 3rd tick while PENDING (or 30th while ACTIVE), it calls `_refresh()` to quietly re-fetch the reservation. Quiet refresh does not show a loading indicator — it just updates the data when ready.

**Estimated cost on form screen**  
The form calculates `(minutes / 60) × $2.00` from the selected date range and shows a live summary card. This matches `PAYMENT_HOURLY_RATE=2.00` in `docker-compose.yml`. The displayed value is informational; the backend is the source of truth.

**Cancel confirmation dialog**  
`showDialog` blocks until the user taps Keep or Cancel Reservation. The API call is only made if `confirmed == true`. This prevents accidental cancellation from a mis-tap.

**Tab filtering is client-side**  
`myReservationsProvider` fetches all reservations in one call. The three `TabBarView` children (`active`, `upcoming`, `past`) each filter the same list in memory. This avoids three parallel API calls and keeps the tab switching instant.

**`isCheckedIn` computed property**  
Rather than a separate `CHECKED_IN` status from the backend, the app derives this from `checkedInAt != null && status == ACTIVE`. This matches how the backend works — check-in sets `checkedInAt`, checkout transitions to `COMPLETED`.

**`/payment/:reservationId` placeholder**  
After a successful checkout, the detail screen shows a "View Receipt" button that navigates to `/payment/:id`. This route is a `PlaceholderScreen` for now — Phase D will replace it with `PaymentReceiptScreen`.

---

## API Endpoints Used

```
POST /reservations                       { spotId, vehiclePlate, reservedFrom, reservedUntil }
GET  /reservations/{id}
GET  /reservations/user/me
POST /reservations/{id}/checkin
POST /reservations/{id}/checkout
POST /reservations/{id}/cancel
```

---

## User Flow

1. Tap a green spot marker on the map → `SpotBottomSheet`
2. Tap **Reserve This Spot** → `ReservationFormScreen`
3. Enter vehicle plate, select From/Until times → see estimated cost
4. Tap **Confirm Reservation** → `POST /reservations` → navigate to `ReservationDetailScreen`
5. Detail screen shows spinner — "Awaiting payment confirmation…" while saga runs
6. Saga completes → status becomes ACTIVE → countdown timer starts
7. Arrive at spot → tap **Check In** → `checkedInAt` set
8. Elapsed time counter starts
9. Leave the spot → tap **Check Out** → status becomes COMPLETED, `totalAmount` shown
10. Tap **View Receipt** → navigates to `/payment/:id` (Phase D)

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/reservation_repository_test.dart
```
