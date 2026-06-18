# Mobile Phase D — Payment Receipt

## What Was Built

A self-contained receipt screen that loads the payment record and its linked reservation in parallel, rendering a formatted receipt card with status, amount, reference number, and full date/time breakdown.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/payment/domain/payment_model.dart` | `PaymentModel` with `PaymentStatus` enum (preAuthorized/captured/refunded/failed); all fields including optional `capturedAt`, `refundedAt`, `refundedAmount` |
| `mobile/lib/features/payment/data/payment_repository.dart` | `GET /payments/{reservationId}` → `PaymentModel`; `paymentRepositoryProvider`; wraps DioException → AppException |
| `mobile/lib/features/payment/presentation/payment_receipt_screen.dart` | Loads payment + reservation in parallel; hero amount card; `_InfoRow` table with reference, spot, plate, duration, timestamps |
| `mobile/test/unit/payment_repository_test.dart` | 6 tests — 200 success, status mapping (CAPTURED/REFUNDED/PRE_AUTHORIZED), amount as double, capturedAt as DateTime, refundedAmount, 404 AppException |

## Files Updated

| File | Change |
|---|---|
| `mobile/lib/core/router/app_router.dart` | `/payment/:reservationId` → `PaymentReceiptScreen(reservationId: ...)` (replaces Phase C placeholder) |

---

## Key Design Decisions

**Parallel data loading**  
`PaymentReceiptScreen.initState()` fires both `paymentRepository.getByReservationId()` and `reservationRepository.getById()` simultaneously via `Future.wait([...])`. The screen waits for both before rendering, avoiding a partial-data flash. The two results are cast from `List<dynamic>` to their typed models.

**Self-contained screen**  
The route takes only `reservationId` as a parameter. The screen fetches everything it needs itself. This means it can be navigated to from any context (receipt button in detail screen, notification tap, deep link) without the caller needing to pass pre-loaded data.

**Receipt back navigation**  
If the user arrived at the receipt via `context.push()` (e.g., from notifications), `context.canPop() → context.pop()` returns them to the previous screen. If the receipt is the first screen (deep link), it falls back to `/home/reservations`.

**Amount display for refunds**  
When `status == REFUNDED`, the displayed amount uses `refundedAmount ?? amount` and shows a `-` prefix. This handles partial refunds correctly.

---

## API Endpoints Used

```
GET /payments/{reservationId}    → PaymentModel
GET /reservations/{id}           → ReservationModel (reuse from Phase C)
```

---

## Receipt Layout

```
┌──────────────────────────────────────┐
│  ✓ (check_circle_outline icon)       │  primaryContainer background (PAID)
│  $4.00                               │
│  [Paid badge]                        │
├──────────────────────────────────────┤
│  Reference      #PAY-UUID8           │
│  ─────────────────────────────────── │
│  Spot           A1                   │
│  Plate          ABC-001              │
│  Duration       2h 00m               │
│  Time           1 Jun 10:00 → 12:00  │
│  ─────────────────────────────────── │
│  Authorised     1 Jun 09:55          │
│  Captured       1 Jun 12:05          │
└──────────────────────────────────────┘
```

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/payment_repository_test.dart
```

In the running app: complete a reservation (create → check in → check out) then tap **View Receipt**.
