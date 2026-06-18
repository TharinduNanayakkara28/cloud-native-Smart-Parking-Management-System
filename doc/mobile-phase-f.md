# Mobile Phase F — Penalties

## What Was Built

Penalty management screen that lists all penalties issued against the user's vehicles, shows tier severity with colour-coded badges, highlights overdue items, and lets the user pay outstanding fines with a confirmation dialog.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/penalty/domain/penalty_model.dart` | `PenaltyModel` with `PenaltyTier` (warning/fine/escalated) and `PenaltyStatus` (issued/paid/waived) enums; `isOverdue` computed property |
| `mobile/lib/features/penalty/data/penalty_repository.dart` | `getMyPenalties()` + `pay(id)`; `myPenaltiesProvider` sorts ISSUED before PAID/WAIVED, newest-first within each group |
| `mobile/lib/features/penalty/presentation/my_penalties_screen.dart` | Card list with tier badge, status chip, vehicle plate, amount, dates, overdue warning, Pay Now button; AlertDialog confirmation before paying |
| `mobile/test/unit/penalty_repository_test.dart` | 11 tests — list/empty/500, tier mapping (FINE/WARNING), pay 200/404/409, isOverdue true/false/paid logic, null notes |

## Files Updated

| File | Change |
|---|---|
| `mobile/lib/core/router/app_router.dart` | `/penalties` → `MyPenaltiesScreen()` (replaces Phase E placeholder) |

---

## Penalty State Machine

```
Overstay detected by penalty-service scheduler
           │
           ▼
       ISSUED (tier: WARNING | FINE | ESCALATED)
      /           \
POST /penalties   timer / auto-escalation
  /{id}/pay           │
      │                ▼
    PAID           ESCALATED (new ISSUED penalty at higher tier)
      or
    WAIVED (admin action only)
```

---

## Tier Badge Colours

| Tier | Badge variant | Meaning |
|---|---|---|
| WARNING | neutral (grey) | First offence — no fine |
| FINE | warning (orange) | $25 overstay fine |
| ESCALATED | error (red) | $50 repeated / serious overstay |

## Status Chip

| Status | Chip variant | Label |
|---|---|---|
| ISSUED (on time) | warning | Issued |
| ISSUED (past dueDate) | error | Overdue |
| PAID | success | Paid |
| WAIVED | neutral | Waived |

---

## Key Design Decisions

**`isOverdue` computed property**  
`PenaltyModel.isOverdue` checks `status == ISSUED && dueDate != null && DateTime.now().isAfter(dueDate!)`. The chip and card highlight change colour automatically. The paid-date check guards against a race where status hasn't synced yet.

**Sort order**  
`myPenaltiesProvider` sorts the flat list: ISSUED penalties first (action required), then PAID/WAIVED, each group sorted newest-first. This keeps outstanding items at the top without a separate API filter parameter.

**Pay confirmation dialog**  
The dialog shows the exact amount and vehicle plate so the user can verify before paying. The API is called only if the user taps "Pay Now". On success, `ref.invalidate(myPenaltiesProvider)` re-fetches so the status chip transitions from "Issued" to "Paid".

**Error on pay shown as SnackBar**  
Unlike form screens (which use `ErrorBanner`), the Pay Now failure is shown as a transient `SnackBar` since the list is still navigable and the failure is one-shot rather than a blocking form error.

---

## API Endpoints Used

```
GET  /penalties/user/me
POST /penalties/{id}/pay
```

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/penalty_repository_test.dart
```

To trigger a penalty in the running app:
```bash
# Check in to a reservation, then wait past reservedUntil without checking out.
# The penalty-service scheduler runs every minute and issues a WARNING penalty.
# Check the Penalties tab — the warning should appear within ~90 seconds.
```
