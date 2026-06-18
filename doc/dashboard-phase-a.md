# Dashboard Phase A — Project Setup, Auth & Core Infrastructure

## What Was Built

Full project scaffold for the React operator dashboard — Vite + Tailwind build pipeline, Zustand auth store with localStorage persistence, Axios JWT interceptor with refresh-and-retry, TanStack Query client, all TypeScript types, shared utility hooks, and the layout shell (Sidebar, Topbar, AppShell).

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/package.json` | Dependencies — React 18, TanStack Query v5, Zustand, Axios, Recharts, react-leaflet, leaflet, react-hook-form, zod, date-fns, lucide-react, Vite, Tailwind |
| `dashboard/vite.config.ts` | Vite config — proxy `/auth`, `/analytics`, `/spots`, `/users` → `http://localhost:8080` |
| `dashboard/tailwind.config.ts` | Tailwind CSS v3 config with `content` paths |
| `dashboard/postcss.config.js` | PostCSS — `tailwindcss` + `autoprefixer` |
| `dashboard/tsconfig.json` | TypeScript — `"moduleResolution": "bundler"`, `"jsx": "react-jsx"`, `noUnusedLocals: false` |
| `dashboard/tsconfig.node.json` | TypeScript config for Vite build scripts |
| `dashboard/index.html` | Root HTML with `<div id="root">` and script to `src/main.tsx` |
| `dashboard/.env.example` | Example env vars — `VITE_API_URL`, `VITE_WS_URL` |
| `dashboard/src/index.css` | Tailwind directives — `@tailwind base/components/utilities` |
| `dashboard/src/types/api.ts` | `OccupancyResponse`, `RevenueResponse`, `ViolationsResponse`, `EventRecord`, `SpotModel`, `LoginResponse`, `UserProfile` |
| `dashboard/src/types/events.ts` | `SpotStateChangedEvent`, `WsMessage` — WebSocket payload types |
| `dashboard/src/store/authStore.ts` | Zustand store with `persist({ name: 'sp-auth' })` — `accessToken`, `refreshToken`, `operator`, `setTokens`, `setOperator`, `logout` |
| `dashboard/src/lib/queryClient.ts` | TanStack `QueryClient` — `staleTime: 30_000`, `retry: 1` |
| `dashboard/src/lib/axios.ts` | Axios instance — request interceptor attaches Bearer; response interceptor: 401 → refresh → retry once; on failure: `logout()` + redirect to `/login` |
| `dashboard/src/hooks/useRevenue.ts` | `GET /analytics/revenue?period=week\|month` — `refetchInterval: 60_000` |
| `dashboard/src/hooks/useOccupancy.ts` | `GET /analytics/occupancy?date=YYYY-MM-DD` — `refetchInterval: 30_000` |
| `dashboard/src/hooks/useViolations.ts` | `GET /analytics/violations` — `refetchInterval: 60_000` |
| `dashboard/src/hooks/useAnalyticsEvents.ts` | `GET /analytics/events?type=&limit=` — no auto-refetch |
| `dashboard/src/hooks/useSpots.ts` | `GET /spots/available?lat=&lng=&radius=` — defaults to `-6.2088, 106.8456`, radius 5000 |
| `dashboard/src/hooks/useSpotUpdates.ts` | Opens native WebSocket, holds callback in `useRef` to prevent reconnect on render, auto-reconnects after 3 s on close |
| `dashboard/src/components/shared/ErrorAlert.tsx` | Flex row with `AlertCircle` icon from lucide-react |
| `dashboard/src/components/shared/LoadingSpinner.tsx` | Animated border div, accepts optional `className` for height override |
| `dashboard/src/components/shared/StatusBadge.tsx` | Pill badge — variants: `success` / `warning` / `error` / `info` / `neutral` |
| `dashboard/src/components/shared/RefreshButton.tsx` | Button with spinning state while async `onRefresh` resolves |
| `dashboard/src/components/shared/DatePicker.tsx` | `<input type="date">` with label, value, onChange, optional max |
| `dashboard/src/components/layout/Sidebar.tsx` | Slate-900 sidebar — NavLinks to all 5 pages; active link = blue-600; mobile overlay with `translate-x` transform |
| `dashboard/src/components/layout/Topbar.tsx` | Initials avatar, operator name, logout button (`logout()` + `navigate('/login')`) |
| `dashboard/src/components/layout/AppShell.tsx` | `useState(sidebarOpen)` — renders Sidebar + Topbar + `<Outlet />` for nested routes |
| `dashboard/src/main.tsx` | `ReactDOM.createRoot`, `QueryClientProvider`, `BrowserRouter`, imports `leaflet/dist/leaflet.css` |
| `dashboard/src/App.tsx` | Routes with `AuthGuard`, nested layout route using `AppShell`, `NotFoundPage` catch-all |

---

## Key Design Decisions

**Vite proxy instead of CORS headers**  
All API paths (`/auth`, `/analytics`, `/spots`, `/users`) are proxied by Vite's dev server to `http://localhost:8080`. No CORS config needed on the backend during development.

**Zustand over Context for auth**  
`useAuthStore.getState()` works outside React components, which is required in the Axios response interceptor (not a hook context). A React Context provider would not be accessible there.

**JWT refresh with `_retry` flag**  
The response interceptor sets `_retry = true` on the first 401 retry attempt. If the retried request also returns 401, the flag prevents infinite looping and triggers logout + redirect.

**`useRef` for stable WebSocket callback**  
`useSpotUpdates` stores the `onUpdate` callback in a `useRef` and updates it on every render (`onUpdateRef.current = onUpdate`). The `useEffect` depends on `[]`, so the WebSocket is opened only once. New function references from the parent never cause reconnection.

**`CircleMarker` instead of Leaflet's default `Marker`**  
The default Leaflet `Marker` requires resolving icon image paths that Vite doesn't bundle correctly without a workaround. `CircleMarker` is a pure SVG element with no external asset dependency.

---

## API Endpoints Used

```
POST /auth/login          { email, password }    → { accessToken, refreshToken }
POST /auth/refresh        { refreshToken }        → { accessToken, refreshToken }
GET  /users/me                                    → UserProfile
WS   /ws/availability                             → SpotStateChangedEvent stream
```

---

## How to Run

```bash
cd dashboard
cp .env.example .env
npm install
npm run dev    # http://localhost:5173
```

All backend API calls proxy to `http://localhost:8080` — start the backend services first.
