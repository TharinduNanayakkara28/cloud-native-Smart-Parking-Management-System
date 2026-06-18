# Operator Dashboard — Implementation Notes

React 18 + TypeScript + Vite SPA located in `dashboard/`.

## Tech Stack

| Concern | Library |
|---|---|
| Routing | React Router v6 |
| Data fetching | TanStack Query v5 |
| Auth state | Zustand + `persist` middleware |
| HTTP client | Axios with JWT interceptors |
| Charts | Recharts |
| Map | react-leaflet + OpenStreetMap |
| Forms | React Hook Form + Zod |
| Styling | Tailwind CSS v3 |
| Build | Vite 5 |

## Directory Structure

```
dashboard/src/
├── App.tsx                  # Routes + AuthGuard
├── main.tsx                 # Entry point — ReactDOM, QueryClient, BrowserRouter
├── index.css                # Tailwind directives
├── types/
│   ├── api.ts               # API response interfaces
│   └── events.ts            # WebSocket message types
├── store/
│   └── authStore.ts         # Zustand: accessToken, refreshToken, operator, logout
├── lib/
│   ├── axios.ts             # Axios instance, Bearer attach, 401 refresh+retry
│   └── queryClient.ts       # TanStack QueryClient (stale 30s, retry 1)
├── hooks/
│   ├── useRevenue.ts        # GET /analytics/revenue?period=week|month  (60s refetch)
│   ├── useOccupancy.ts      # GET /analytics/occupancy?date=YYYY-MM-DD  (30s refetch)
│   ├── useViolations.ts     # GET /analytics/violations                  (60s refetch)
│   ├── useAnalyticsEvents.ts# GET /analytics/events?type=&limit=
│   ├── useSpots.ts          # GET /spots/available?lat=&lng=&radius=
│   └── useSpotUpdates.ts    # WebSocket ws://…/ws/availability, auto-reconnect 3s
├── components/
│   ├── shared/              # ErrorAlert, LoadingSpinner, StatusBadge, RefreshButton, DatePicker
│   ├── layout/              # AppShell (Outlet), Sidebar (NavLink), Topbar (logout)
│   ├── dashboard/           # KpiCard, RevenueChart (7-day line), ViolationPieChart (donut)
│   ├── occupancy/           # SpotMap (react-leaflet CircleMarker), HourlyBarChart
│   ├── revenue/             # RevenueLineChart (gross+net), PeriodSelector (week/month)
│   ├── violations/          # ViolationsTable (tier filter), TierBadge
│   └── events/              # EventsTable (topic colour-coded, mini prop for dashboard)
└── pages/
    ├── LoginPage.tsx        # React Hook Form + Zod, POST /auth/login + GET /users/me
    ├── DashboardPage.tsx    # 4 KPIs + revenue mini-chart + violation donut + hourly bar + event log
    ├── OccupancyPage.tsx    # Live map with WebSocket overlay + status sidebar + hourly bar
    ├── RevenuePage.tsx      # Period selector + KPI strip + gross/net line chart + transactions table
    ├── ViolationsPage.tsx   # Summary cards + tier donut + filtered penalty events table
    ├── EventsPage.tsx       # Type/limit selectors + topic colour-coded event log
    └── NotFoundPage.tsx
```

## Authentication Flow

1. `LoginPage` submits to `POST /auth/login`, stores `accessToken` + `refreshToken` in Zustand.
2. `AuthGuard` in `App.tsx` redirects to `/login` when `accessToken` is null.
3. Every Axios request attaches `Authorization: Bearer <token>` via request interceptor.
4. On 401, the response interceptor calls `POST /auth/refresh`, retries the original request once.
5. If refresh also fails, calls `useAuthStore.getState().logout()` then redirects to `/login`.
6. Logout (Topbar) clears tokens; Zustand `persist` writes to `localStorage` under key `sp-auth`.

## Live Map — Key Decision

`CircleMarker` is used instead of Leaflet's default `Marker` to avoid the known Vite/webpack icon asset-path issue. No icon path patching required.

WebSocket state updates are merged on top of the REST snapshot: `state: spotStates[s.id] ?? s.state`.

## Revenue Chart — Real Data

`RevenueLineChart` derives per-day gross and net from `GET /analytics/events?type=payment.captured` and `payment.refunded`, grouping events by `eventTime.slice(0, 10)`. This avoids relying on the analytics aggregate endpoints for the daily breakdown.

## Running Locally

```bash
cd dashboard
cp .env.example .env      # set VITE_API_URL / VITE_WS_URL if needed
npm install
npm run dev               # http://localhost:5173
```

Vite proxies `/auth`, `/analytics`, `/spots`, `/users` to `http://localhost:8080`.
