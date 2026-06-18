# Mobile Phase E — Notifications

## What Was Built

In-app notification centre with unread count badge on the navigation bar, local push notification support for penalty alerts, and deep navigation from notification taps to the relevant reservation, payment, or penalty screen.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/lib/features/notification/domain/notification_model.dart` | `NotificationModel {id, userId, type, title, message, channel, read, createdAt, readAt?, entityId?}` with `fromJson`; `entityId` is optional for deep navigation |
| `mobile/lib/features/notification/data/notification_repository.dart` | `getMyNotifications`, `getUnreadCount`, `markAsRead`, `markAllRead`; `notificationsProvider` FutureProvider; `unreadCountProvider` StreamProvider that polls every 30 s |
| `mobile/lib/features/notification/presentation/notifications_screen.dart` | Sorted newest-first list; unread items highlighted with left border + tinted background; pull-to-refresh; per-item and bulk read; deep navigation on tap; local push for penalty notifications on first load |
| `mobile/lib/core/notifications/local_notification_service.dart` | `init()` sets up `flutter_local_notifications` with Android channel `penalty_alerts` (high importance); `showPenaltyAlert({title, body})` fires an OS-level push notification |
| `mobile/test/unit/notification_repository_test.dart` | 10 tests — list/empty/500, unread count 3/0, markAsRead endpoint, markAllRead endpoint, `read` default false, nullable entityId, readAt null/parsed |

## Files Updated

| File | Change |
|---|---|
| `mobile/lib/main.dart` | Added `await LocalNotificationService.init()` before `runApp` |
| `mobile/lib/core/router/app_router.dart` | `/home/notifications` → `NotificationsScreen`; `/penalties` placeholder added for Phase F; `_HomeShell` changed from `StatelessWidget` to `ConsumerWidget`; watches `unreadCountProvider` to show `Badge` on notifications tab |

---

## Key Design Decisions

**`unreadCountProvider` as `StreamProvider.autoDispose`**  
Uses an `async*` generator with a `while(true)` + 30 s delay — the same reconnect pattern as the WebSocket. When the user is on another tab and the provider has no listeners, `autoDispose` kills the polling loop automatically. This avoids background network calls when the badge isn't visible.

**`ref.listen` for penalty push trigger**  
`NotificationsScreen` uses `ref.listen<AsyncValue<List<NotificationModel>>>` to detect the transition `previous.isLoading → next.hasValue`. When that transition occurs and there are unread penalty notifications, it fires `LocalNotificationService.showPenaltyAlert()`. Firing inside `ref.listen` instead of inside the `data` builder prevents re-triggering on every widget rebuild.

**Optimistic read-and-invalidate**  
Tapping a notification calls `markAsRead` and then `ref.invalidate(notificationsProvider)` and `ref.invalidate(unreadCountProvider)`. The list and badge both update on the next provider re-fetch. No local state mutation is needed.

**`entityId` for deep navigation**  
`NotificationModel` includes an optional `entityId` field. When present and the type contains `RESERVATION`, the tap navigates to `/reservation/{entityId}`. `PAYMENT` → `/payment/{entityId}`. `PENALTY` → `/penalties` (Phase F placeholder, entityId not required). When `entityId` is null or type is unknown, tapping only marks as read with no navigation.

**`_HomeShell` converted to `ConsumerWidget`**  
The shell widget now watches `unreadCountProvider` and wraps the Notifications `NavigationDestination` icon in a Material 3 `Badge` widget. The badge hides when count is 0 (`isLabelVisible: unreadCount > 0`) and caps at "99+" for display.

---

## API Endpoints Used

```
GET  /notifications/user/me                    → List<NotificationModel>
GET  /notifications/user/me/unread-count       → { unreadCount: int }
POST /notifications/{id}/read
POST /notifications/user/me/read-all
```

---

## Platform Setup Required

**Android** — add `VIBRATE` permission to `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.VIBRATE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

**iOS** — `LocalNotificationService.init()` already requests `alert`, `badge`, and `sound` permissions at runtime via `DarwinInitializationSettings`.

---

## Notification Type → Navigation Mapping

| Type (contains) | Navigates to |
|---|---|
| `RESERVATION` + entityId | `/reservation/{entityId}` |
| `PAYMENT` + entityId | `/payment/{entityId}` |
| `PENALTY` | `/penalties` (Phase F) |
| anything else | marks read only |

---

## How to Test

```bash
cd mobile
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/notification_repository_test.dart
```

To trigger test notifications via the backend:
```bash
# Create and complete a reservation — the notification service publishes events on every status change
# Then open Notifications tab — each event should appear as an in-app notification
```

The unread badge appears on the bottom nav within 30 s of a new unread notification arriving.
