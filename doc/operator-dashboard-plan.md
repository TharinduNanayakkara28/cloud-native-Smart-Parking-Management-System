# Operator Web Dashboard — Full Implementation Plan

## Overview

A React + TypeScript single-page application for parking lot operators. Operators monitor live occupancy, track revenue, manage violations, view analytics charts, and receive real-time spot state updates — all powered by the existing Spring Boot backend.

---

## Tech Stack

| Concern | Library / Tool | Why |
|---|---|---|
| Framework | React 18 + TypeScript | Industry standard; strong typing |
| Build Tool | Vite | Fast dev server + HMR |
| Styling | Tailwind CSS + shadcn/ui | Utility-first; pre-built accessible components |
| State / Server Cache | TanStack Query (React Query) v5 | Auto-refetch, caching, loading/error states |
| Auth State | Zustand | Lightweight client state for token + user |
| Charts | Recharts | Composable, Recharts works well with React |
| Maps | react-leaflet + OpenStreetMap | Spot occupancy map |
| WebSocket | native browser WebSocket | Real-time spot updates |
| Forms | React Hook Form + Zod | Validation with TypeScript inference |
| Date handling | date-fns | Lightweight, tree-shakable |
| HTTP | Axios | Interceptors for JWT attach + refresh |
| Routing | React Router v6 | Standard SPA routing |
| Testing | Vitest + React Testing Library | Fast, Jest-compatible |

---

## Project Structure

```
dashboard/
├── src/
│   ├── main.tsx
│   ├── App.tsx                         # Router + QueryClient + AuthGuard
│   ├── lib/
│   │   ├── axios.ts                    # Axios instance + interceptors
│   │   ├── queryClient.ts              # TanStack Query client config
│   │   └── websocket.ts               # WebSocket singleton
│   ├── store/
│   │   └── authStore.ts               # Zustand: token + operator user
│   ├── types/
│   │   ├── api.ts                      # All API response types
│   │   └── events.ts                   # WebSocket event types
│   ├── hooks/
│   │   ├── useAuth.ts
│   │   ├── useOccupancy.ts
│   │   ├── useRevenue.ts
│   │   ├── useViolations.ts
│   │   ├── useAnalyticsEvents.ts
│   │   └── useSpotUpdates.ts          # WebSocket hook
│   ├── pages/
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx          # Overview — KPI cards + charts
│   │   ├── OccupancyPage.tsx          # Live map + hourly breakdown
│   │   ├── RevenuePage.tsx            # Revenue charts + filters
│   │   ├── ViolationsPage.tsx         # Penalty list + tier breakdown
│   │   ├── EventsPage.tsx             # Raw event log
│   │   └── NotFoundPage.tsx
│   ├── components/
│   │   ├── layout/
│   │   │   ├── AppShell.tsx           # Sidebar + topbar wrapper
│   │   │   ├── Sidebar.tsx
│   │   │   └── Topbar.tsx
│   │   ├── dashboard/
│   │   │   ├── KpiCard.tsx
│   │   │   ├── ReservationRateChart.tsx
│   │   │   ├── RevenueChart.tsx
│   │   │   └── ViolationPieChart.tsx
│   │   ├── occupancy/
│   │   │   ├── SpotMap.tsx
│   │   │   ├── SpotMarker.tsx
│   │   │   └── HourlyBarChart.tsx
│   │   ├── revenue/
│   │   │   ├── RevenueLineChart.tsx
│   │   │   └── PeriodSelector.tsx
│   │   ├── violations/
│   │   │   ├── ViolationsTable.tsx
│   │   │   └── TierBadge.tsx
│   │   ├── events/
│   │   │   └── EventsTable.tsx
│   │   └── shared/
│   │       ├── DatePicker.tsx
│   │       ├── ErrorAlert.tsx
│   │       ├── LoadingSpinner.tsx
│   │       ├── StatusBadge.tsx
│   │       └── RefreshButton.tsx
│   └── router/
│       └── index.tsx
├── public/
├── index.html
├── vite.config.ts
├── tailwind.config.ts
├── tsconfig.json
├── package.json
└── README.md
```

---

## Pages & Routing

```
/login                  LoginPage          (public)
/                       DashboardPage      (overview KPIs + mini charts)
/occupancy              OccupancyPage      (live map + date filter)
/revenue                RevenuePage        (line/bar chart + week|month toggle)
/violations             ViolationsPage     (table + tier breakdown pie)
/events                 EventsPage         (raw event log + type filter)
*                       NotFoundPage
```

All routes except `/login` are wrapped in `<AuthGuard>` — redirects to `/login` if no token in Zustand store.

---

## Page Breakdown

---

### LoginPage

**API:** `POST /auth/login`

**Layout:** Centered card — email + password form, Submit button.

**Logic:**
- Submit via React Hook Form + Zod (`{ email: z.string().email(), password: z.string().min(6) }`)
- On success: store `accessToken` + `refreshToken` in Zustand + `localStorage`
- Navigate to `/`
- Show error banner on 401

---

### DashboardPage — Overview

**APIs:**
```
GET /analytics/revenue?period=week
GET /analytics/violations
GET /analytics/occupancy?date=<today>
GET /analytics/events?limit=10
```

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  KPI Cards (row of 4)                                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │Net Revenue│ │Reservations│ │Violations│ │UpTime    │     │
│  │ $130.00  │ │    42     │ │    12    │ │ 99.8%   │       │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
├────────────────────────┬────────────────────────────────────┤
│  Revenue (this week)   │   Violation Breakdown              │
│  [RevenueLineChart]    │   [ViolationPieChart]              │
│                        │   Tier 1: 8 | Tier 2: 3 | T3: 1   │
├────────────────────────┴────────────────────────────────────┤
│  Today's Hourly Occupancy (bar chart)                       │
│  [HourlyBarChart]                                           │
├─────────────────────────────────────────────────────────────┤
│  Recent Events (last 10)                                    │
│  [EventsTable mini view]                                    │
└─────────────────────────────────────────────────────────────┘
```

**Hooks:**
- `useRevenue('week')` — TanStack Query wrapping `GET /analytics/revenue?period=week`
- `useViolations()` — TanStack Query wrapping `GET /analytics/violations`
- `useOccupancy(today)` — TanStack Query wrapping `GET /analytics/occupancy?date=<today>`
- Auto-refetch every 60 seconds

---

### OccupancyPage — Live Map

**APIs:**
```
GET /spots/available?lat=&lng=&radius=2000   (initial load)
WS  /ws/availability                          (real-time updates)
GET /analytics/occupancy?date=<selected>      (hourly breakdown)
```

**Layout:**

```
┌──────────────────────────────────────────────────────────────┐
│  Date Picker  [Today ▼]     Auto-refresh: ON                 │
├───────────────────────┬──────────────────────────────────────┤
│                       │  Summary                             │
│   [SpotMap]           │  Total spots: 18                     │
│                       │  Free:       11  (green)             │
│   Green = free        │  Reserved:    3  (amber)             │
│   Amber  = reserved   │  Occupied:    4  (red)               │
│   Red    = occupied   │                                      │
│                       ├──────────────────────────────────────┤
│                       │  Hourly Reservations                 │
│                       │  [HourlyBarChart]                    │
└───────────────────────┴──────────────────────────────────────┘
```

**Key implementation:**
- `react-leaflet` `MapContainer` renders spot markers
- `useSpotUpdates()` hook opens `WebSocket` to `/ws/availability`, updates local spot state map on each `spot.state.changed` message
- Colour-codes markers: FREE=green, RESERVED=amber, OCCUPIED=red
- Clicking a marker shows a tooltip: spot number, floor, current state, last updated

**WebSocket hook:**
```ts
function useSpotUpdates(onUpdate: (spotId: string, state: string) => void) {
  useEffect(() => {
    const ws = new WebSocket('ws://localhost:8080/ws/availability');
    ws.onmessage = (e) => {
      const event = JSON.parse(e.data);
      if (event.eventType === 'spot.state.changed') {
        onUpdate(event.payload.spotId, event.payload.state);
      }
    };
    return () => ws.close();
  }, []);
}
```

---

### RevenuePage

**APIs:**
```
GET /analytics/revenue?period=week
GET /analytics/revenue?period=month
```

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  Period:  [This Week]  [This Month]                         │
├──────────────────────────┬──────────────────────────────────┤
│  KPI Strip               │                                  │
│  Gross: $150.00          │  [RevenueLineChart]              │
│  Refunded: $20.00        │  Gross vs Net over time          │
│  Net: $130.00            │                                  │
│  Transactions: 35        │                                  │
├──────────────────────────┴──────────────────────────────────┤
│  Breakdown Table                                            │
│  Date | Gross | Refunded | Net | Transactions               │
│  (derived from raw events via GET /analytics/events)        │
└─────────────────────────────────────────────────────────────┘
```

**Chart:** `Recharts LineChart` with two lines — Gross Revenue (blue) and Net Revenue (green) over days.

---

### ViolationsPage

**APIs:**
```
GET /analytics/violations
GET /analytics/events?type=penalty.issued&limit=100
```

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  Summary Cards                                              │
│  Total: 12 | Tier 1 Warnings: 8 | Tier 2 Fines: 3 | Tier 3: 1│
├────────────────────┬────────────────────────────────────────┤
│  [ViolationPieChart│  Violations Table                      │
│   Tier breakdown]  │  Time | User | Spot | Tier | Amount    │
│                    │  (from analytics events)               │
│                    │                                        │
│                    │  Filter: [All ▼] [Tier 1] [Tier 2] [T3]│
└────────────────────┴────────────────────────────────────────┘
```

**Chart:** `Recharts PieChart` with three slices — Tier 1 (grey), Tier 2 (orange), Tier 3 (red).

**Table columns:** Issued At | Entity ID | Tier | Type | Amount

---

### EventsPage — Raw Event Log

**API:**
```
GET /analytics/events?type=<filter>&limit=<50|100|200>
```

**Layout:**

```
┌─────────────────────────────────────────────────────────────┐
│  Filter by type: [All ▼]   Limit: [50 ▼]   [Refresh]       │
├─────────────────────────────────────────────────────────────┤
│  EventsTable                                                │
│  Time | Event Type | Topic | User ID | Entity ID | Amount   │
│  (colour-coded rows by topic)                               │
└─────────────────────────────────────────────────────────────┘
```

Useful for debugging the event pipeline end-to-end without opening a database client.

---

## API Integration Layer

### Axios Instance

```ts
// src/lib/axios.ts
const api = axios.create({ baseURL: import.meta.env.VITE_API_URL });

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

api.interceptors.response.use(
  (res) => res,
  async (error) => {
    if (error.response?.status === 401 && !error.config._retry) {
      error.config._retry = true;
      const refreshToken = useAuthStore.getState().refreshToken;
      const { data } = await axios.post('/auth/refresh', { refreshToken });
      useAuthStore.getState().setTokens(data.accessToken, data.refreshToken);
      error.config.headers.Authorization = `Bearer ${data.accessToken}`;
      return api(error.config);
    }
    return Promise.reject(error);
  }
);
```

### TanStack Query Hooks

```ts
// src/hooks/useRevenue.ts
export function useRevenue(period: 'week' | 'month') {
  return useQuery({
    queryKey: ['revenue', period],
    queryFn: () => api.get(`/analytics/revenue?period=${period}`).then(r => r.data),
    refetchInterval: 60_000,
  });
}

// src/hooks/useOccupancy.ts
export function useOccupancy(date: string) {
  return useQuery({
    queryKey: ['occupancy', date],
    queryFn: () => api.get(`/analytics/occupancy?date=${date}`).then(r => r.data),
    refetchInterval: 30_000,
  });
}

// src/hooks/useViolations.ts
export function useViolations() {
  return useQuery({
    queryKey: ['violations'],
    queryFn: () => api.get('/analytics/violations').then(r => r.data),
    refetchInterval: 60_000,
  });
}
```

---

## Auth Store (Zustand)

```ts
// src/store/authStore.ts
interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  operator: { id: string; name: string; email: string } | null;
  setTokens: (access: string, refresh: string) => void;
  setOperator: (op: AuthState['operator']) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      operator: null,
      setTokens: (access, refresh) => set({ accessToken: access, refreshToken: refresh }),
      setOperator: (op) => set({ operator: op }),
      logout: () => set({ accessToken: null, refreshToken: null, operator: null }),
    }),
    { name: 'auth-storage' }
  )
);
```

---

## Key TypeScript Types

```ts
// src/types/api.ts

interface OccupancyResponse {
  date: string;
  totalReservations: number;
  hourlyBreakdown: { hour: string; reservationCount: number }[];
}

interface RevenueResponse {
  period: string;
  periodStart: string;
  grossRevenue: number;
  refundedAmount: number;
  netRevenue: number;
  transactionCount: number;
}

interface ViolationsResponse {
  totalViolations: number;
  byTier: { tier: number; type: string; count: number }[];
}

interface EventRecord {
  id: string;
  eventType: string;
  topic: string;
  userId: string | null;
  entityId: string | null;
  amount: number | null;
  tier: number | null;
  eventTime: string;
  receivedAt: string;
}
```

---

## Responsive Layout

```
Desktop (≥1024px)   Sidebar (240px fixed) + main content area
Tablet (768-1023px) Collapsible sidebar + hamburger menu
Mobile (<768px)     Bottom nav bar (operators unlikely, but accessible)
```

Tailwind breakpoints: `lg:` for sidebar visibility, `sm:` for chart sizing.

---

## Testing Strategy

| Layer | Tool | What to test |
|---|---|---|
| Hooks | Vitest + `renderHook` + MSW | API responses, loading + error states |
| Components | React Testing Library | KpiCard renders value, chart renders without crashing |
| Pages | React Testing Library + MSW | LoginPage form validation, DashboardPage data display |
| E2E (optional) | Playwright | Login → view dashboard → filter revenue → check violations |

**MSW (Mock Service Worker)** intercepts API calls in tests so no real backend is needed:
```ts
const handlers = [
  http.get('/analytics/revenue', () => HttpResponse.json(mockRevenue)),
  http.get('/analytics/violations', () => HttpResponse.json(mockViolations)),
];
```

---

## Implementation Phases

| Phase | Deliverable | Key APIs |
|---|---|---|
| A | Project setup, routing, auth, Axios + JWT refresh | `POST /auth/login` |
| B | Dashboard overview page — KPI cards + charts | `/analytics/revenue`, `/analytics/violations`, `/analytics/occupancy` |
| C | Occupancy page — live map + WebSocket updates | `WS /ws/availability`, `GET /spots/available` |
| D | Revenue page — line chart + period toggle | `GET /analytics/revenue` |
| E | Violations page — pie chart + filtered table | `GET /analytics/violations`, `GET /analytics/events` |
| F | Events page — raw event log with type filter | `GET /analytics/events` |
| G | Polish — responsive layout, loading skeletons, error boundaries | — |

---

## Setup

```bash
# Prerequisites: Node 20+

cd dashboard
npm install

# Development server (proxies /api → localhost:8080)
npm run dev

# Run tests
npm run test

# Build for production
npm run build

# Preview production build
npm run preview
```

**.env.local:**
```env
VITE_API_URL=http://localhost:8080
VITE_WS_URL=ws://localhost:8080/ws/availability
```

**vite.config.ts proxy** (avoids CORS in development):
```ts
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/auth': 'http://localhost:8080',
      '/analytics': 'http://localhost:8080',
      '/spots': 'http://localhost:8080',
    }
  }
});
```

---

## Summary — What the Operator Sees

| Screen | Real-time? | Data source |
|---|---|---|
| Dashboard | Auto-refresh 60s | Analytics API |
| Occupancy Map | Live (WebSocket) | Availability WS + Analytics API |
| Revenue | Auto-refresh 60s | Analytics API |
| Violations | Auto-refresh 60s | Analytics API |
| Event Log | Manual refresh | Analytics API |
