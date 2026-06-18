# Mobile Phase A — Auth

## What Was Built

Full authentication flow for the Smart Parking driver app — registration, login, secure token storage, automatic JWT refresh, and GoRouter-based auth guard.

---

## Files Created

| File | Purpose |
|---|---|
| `mobile/pubspec.yaml` | Project dependencies — flutter_riverpod, Dio, go_router, flutter_secure_storage, reactive_forms, flutter_map, web_socket_channel |
| `mobile/lib/main.dart` | App entry point — wraps in `ProviderScope` |
| `mobile/lib/app.dart` | `MaterialApp.router` wired to `routerProvider` |
| `mobile/lib/core/api/endpoints.dart` | `kBaseUrl` and `kWsUrl` via `--dart-define`; defaults to `10.0.2.2:8080` (Android emulator host) |
| `mobile/lib/core/api/auth_interceptor.dart` | Attaches `Authorization: Bearer` on every request; on 401 calls `/auth/refresh` with a separate Dio instance to avoid infinite recursion; clears tokens on second 401 |
| `mobile/lib/core/api/api_client.dart` | Creates main Dio with `AuthInterceptor` + a second token-refresh Dio without any interceptors |
| `mobile/lib/core/auth/token_storage.dart` | `flutter_secure_storage` wrapper — Keychain on iOS, EncryptedSharedPrefs on Android; Riverpod `Provider` |
| `mobile/lib/core/auth/auth_notifier.dart` | `AsyncNotifier<UserModel?>` — `build()` reads stored token and calls `GET /users/me`; `login()`, `register()`, `logout()` |
| `mobile/lib/core/exceptions/app_exception.dart` | Parses ProblemDetail JSON `{status, title, detail}` from DioException into a typed `AppException` |
| `mobile/lib/core/router/app_router.dart` | GoRouter with `_RouterNotifier` (ChangeNotifier watching auth state) — redirects unauthenticated → `/login`; authenticated away from auth pages → `/home/map` |
| `mobile/lib/features/auth/domain/user_model.dart` | `UserModel {id, name, email, phone}` with `fromJson` |
| `mobile/lib/features/auth/data/auth_repository.dart` | `login()`, `register()`, `getProfile()` — each wraps DioException into AppException |
| `mobile/lib/features/auth/presentation/login_screen.dart` | ReactiveForm — email + password fields, `ErrorBanner` on failure, `LoadingButton` while submitting |
| `mobile/lib/features/auth/presentation/register_screen.dart` | ReactiveForm — name, email, phone, password, confirm password; `mustMatch` validator for passwords |
| `mobile/lib/shared/theme/app_theme.dart` | Material 3 theme seeded from `Color(0xFF1B72E8)` |
| `mobile/lib/shared/widgets/error_banner.dart` | Coloured error container using `colorScheme.errorContainer` |
| `mobile/lib/shared/widgets/loading_button.dart` | `FilledButton` that swaps label for `CircularProgressIndicator` while `isLoading` |
| `mobile/lib/shared/widgets/placeholder_screen.dart` | Stub scaffold used for routes not yet implemented in later phases |
| `mobile/test/unit/auth_repository_test.dart` | 5 tests — login 200, login 401, register 201, register 409, getProfile 200/401 |
| `mobile/test/unit/auth_notifier_test.dart` | 6 tests — build with no token, build with valid token, build clears on getProfile error, login success/error, logout, register success |

---

## Key Design Decisions

**Separate Dio for token refresh**  
`AuthInterceptor` is constructed with two Dio instances: the main one (with the interceptor) and a bare `refreshDio` (no interceptors). When a 401 triggers a refresh, `refreshDio` is used to call `/auth/refresh`. This avoids the interceptor calling itself recursively.

**Token Storage on device**  
`flutter_secure_storage` stores `access_token` and `refresh_token` in the platform keystore. The app reads them on cold start in `AuthNotifier.build()` — if a stored token exists, it calls `/users/me` to verify and rehydrate the user. Expired tokens are caught, cleared, and the user is sent to login.

**GoRouter + Riverpod redirect**  
`_RouterNotifier` is a `ChangeNotifier` that calls `notifyListeners()` whenever `authNotifierProvider` emits. GoRouter calls the `redirect` function on each notification, making auth-gated routing reactive without boilerplate.

**Reactive Forms validation**  
`reactive_forms` gives compile-time form control names and built-in validators (`Validators.email`, `Validators.minLength`, `Validators.mustMatch`). This avoids manual `TextEditingController` bookkeeping.

---

## API Endpoints Used

```
POST /auth/register    { name, email, password, phone }
POST /auth/login       { email, password }
POST /auth/refresh     { refreshToken }
GET  /users/me
```

---

## Platform Setup Required

Before `flutter run`, add location permissions to platform manifests (needed by Phase B):

**Android** — `android/app/src/main/AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

**iOS** — `ios/Runner/Info.plist`:
```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>Used to find parking spots near your location</string>
```

---

## How to Run

```bash
cd mobile
flutter pub get
flutter run -d android   # or -d ios
```

To run tests (generate mocks first):
```bash
flutter pub run build_runner build --delete-conflicting-outputs
flutter test test/unit/auth_repository_test.dart
flutter test test/unit/auth_notifier_test.dart
```
