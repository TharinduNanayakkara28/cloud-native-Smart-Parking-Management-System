# Dashboard Phase G — Layout, Auth Flow & Polish

## What Was Built

Complete application shell with responsive sidebar navigation, authenticated routing guard, login page, logout flow, loading skeletons throughout, and error alerts — making the dashboard production-ready end-to-end.

---

## Files Updated / Finalised

| File | What Was Finalised |
|---|---|
| `dashboard/src/App.tsx` | `AuthGuard` component wrapping all protected routes; nested route layout with `AppShell`; catch-all `NotFoundPage` |
| `dashboard/src/main.tsx` | `ReactDOM.createRoot`, `QueryClientProvider`, `BrowserRouter` wired together; `leaflet/dist/leaflet.css` imported here so it applies globally |
| `dashboard/src/components/layout/AppShell.tsx` | `sidebarOpen` state toggled by Topbar hamburger; Sidebar receives `open` + `onClose` props; `<Outlet />` renders the active page |
| `dashboard/src/components/layout/Sidebar.tsx` | `NavLink` from react-router-dom — active link gets `text-blue-600 bg-blue-50`; mobile overlay slides in/out with `translate-x-0 / -translate-x-full`; backdrop click closes |
| `dashboard/src/components/layout/Topbar.tsx` | Operator initials avatar; name from Zustand `operator.name`; logout calls `useAuthStore.getState().logout()` then `navigate('/login')` |
| `dashboard/src/pages/LoginPage.tsx` | React Hook Form + Zod (`email` + `password min(6)`); `useMutation` calls `POST /auth/login`; on success fetches `/users/me` and calls `setOperator`; error shows `ErrorAlert` |
| `dashboard/src/pages/NotFoundPage.tsx` | Large grey "404", message, Back to Dashboard link |

---

## Auth Flow (End-to-End)

```
User visits /
  └── AuthGuard: accessToken === null?
        ├── Yes → <Navigate to="/login" />
        └── No  → <AppShell><Outlet /></AppShell>

LoginPage submits → POST /auth/login
  ├── Success: setTokens(access, refresh) + GET /users/me → setOperator → navigate('/')
  └── 401: ErrorAlert "Invalid email or password"

Axios response interceptor: any 401 during session
  ├── First attempt: POST /auth/refresh → retry original request
  └── Second 401: logout() + window.location.href = '/login'

Topbar logout button: logout() + navigate('/login')
  └── Zustand clears accessToken/refreshToken/operator
  └── persist middleware removes 'sp-auth' from localStorage
```

---

## Responsive Layout

```
Desktop (≥ 1024px)
  ┌──────────────────────────────────────────────────┐
  │ [Sidebar 240px fixed] │ [Topbar]                 │
  │                       │ [Page content]           │
  └──────────────────────────────────────────────────┘

Mobile (< 1024px)
  ┌──────────────────────────────────────────────────┐
  │ [Topbar — hamburger ☰]                           │
  │ [Page content]                                   │
  └──────────────────────────────────────────────────┘
  Sidebar slides in as overlay from the left when hamburger is tapped.
  Backdrop click or NavLink click closes it.
```

---

## Loading Skeleton Pattern

Every data-dependent section uses one of two skeleton patterns:

**Chart skeleton** (height-fixed placeholder):
```tsx
if (loading) return <div className="h-44 bg-gray-50 rounded-lg animate-pulse" />;
```

**KPI card skeleton** (preserves card layout):
```tsx
{loading
  ? <div className="h-8 w-28 bg-gray-100 rounded-lg animate-pulse" />
  : <p className="text-2xl font-bold text-gray-900">{value}</p>}
```

**Table skeleton** (row placeholders):
```tsx
{Array.from({ length: 5 }).map((_, i) => (
  <div key={i} className="h-8 bg-gray-100 rounded animate-pulse" />
))}
```

---

## Error Handling Pattern

Every page shows `<ErrorAlert message="…" />` when a query errors. The rest of the page still renders — cached data or empty states are displayed rather than a full-page error. This matches the operator's need to see partial data rather than a blank screen on a single API failure.

```tsx
{error && <ErrorAlert message="Failed to load revenue data." />}
```

`ErrorAlert` is a flex row with a `AlertCircle` (lucide-react) icon and the message string — subtle but visible.

---

## Key Design Decisions

**`AuthGuard` as a wrapper, not a HOC**  
`AuthGuard` is a plain component wrapping `children` with a conditional redirect. It reads `accessToken` directly from `useAuthStore` (synchronous Zustand read), so there's no async flash before redirecting.

**Zustand + `persist` for tokens**  
Tokens survive page refresh via `persist({ name: 'sp-auth' })`. On hard refresh, `AuthGuard` reads the stored token from `localStorage` and immediately renders the protected route without showing `/login` first.

**Operator name fetched at login**  
After `POST /auth/login` succeeds, `LoginPage` immediately calls `GET /users/me` and stores the result in `useAuthStore`. This means `Topbar` can read `operator.name` synchronously without any additional fetch.

---

## How to Run the Full Dashboard

```bash
# Prerequisites: Node 20+, backend services running on :8080

cd dashboard
cp .env.example .env       # defaults point to localhost:8080
npm install
npm run dev                # http://localhost:5173

# Build for production
npm run build
npm run preview            # preview the production build on :4173
```

Log in with operator credentials registered via `POST /auth/register` (or pre-seeded in the backend).
