# Dashboard Phase C — Live Occupancy Map

## What Was Built

A real-time parking lot map showing every spot's current state via colour-coded circle markers, overlaid with WebSocket updates as they arrive. A sidebar shows live counts and an occupancy bar, and a date picker drives the hourly-reservations chart below.

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/src/components/occupancy/SpotMap.tsx` | `react-leaflet` `MapContainer` with `CircleMarker` per spot; `Tooltip` on hover showing spot number, floor, state |
| `dashboard/src/components/occupancy/HourlyBarChart.tsx` | Recharts `BarChart` of `hourlyBreakdown` — reservations by hour; empty-state message when no data |
| `dashboard/src/pages/OccupancyPage.tsx` | Loads spots via REST, applies WebSocket state overrides, computes free/reserved/occupied counts, renders map + sidebar + hourly chart |

---

## Page Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Live Occupancy           Date: [2025-06-18]   ● Live        │
├───────────────────────────────┬──────────────────────────────┤
│                               │  Current Status              │
│   [SpotMap — react-leaflet]   │  Total Spots:  18            │
│                               │  Free:         11  (green)   │
│   Zoom, pan, click markers    │  Reserved:      3  (amber)   │
│                               │  Occupied:      4  (red)     │
│   ● Free (green)              │  ━━━━━━━━━━━━━━━━━━━━━━━━━  │
│   ● Reserved (amber)          │  [occupancy bar — 3 colours] │
│   ● Occupied (red)            │  22% occupied                │
│                               ├──────────────────────────────┤
│  [Legend row]                 │  Hourly Reservations         │
│                               │  [HourlyBarChart]            │
└───────────────────────────────┴──────────────────────────────┘
```

---

## WebSocket Integration

`useSpotUpdates(callback)` is called in `OccupancyPage`. Each received message updates a local `spotStates` map:

```ts
const [spotStates, setSpotStates] = useState<Record<string, SpotModel['state']>>({});

const handleSpotUpdate = useCallback((spotId: string, state: SpotModel['state']) => {
  setSpotStates((prev) => ({ ...prev, [spotId]: state }));
}, []);

useSpotUpdates(handleSpotUpdate);

// Merge REST snapshot with live overrides
const spots = (rawSpots ?? []).map((s) => ({
  ...s,
  state: spotStates[s.id] ?? s.state,
}));
```

If the REST load hasn't completed yet, the map is in loading state. Once loaded, WebSocket events override individual spots in real time without re-fetching the full list.

---

## Key Design Decisions

**`CircleMarker` instead of default `Marker`**  
Leaflet's default marker icon relies on image file paths that Vite doesn't resolve correctly without manual patching. `CircleMarker` is a pure SVG primitive with no external asset dependency.

**Stable callback ref in `useSpotUpdates`**  
The hook uses `useRef` to hold the latest `onUpdate` callback. The `useEffect` closes over the ref (not the callback directly), so the WebSocket is opened once and never reconnects when `OccupancyPage` re-renders.

**Auto-reconnect on close**  
`ws.onclose` schedules `setTimeout(connect, 3000)`. The cleanup function from `useEffect` cancels the timeout and closes the socket, preventing reconnect attempts after the component unmounts.

**Overlay bar for occupancy**  
The three-segment coloured bar in the sidebar (`bg-green-400 / bg-amber-400 / bg-red-400`) is sized with inline `width` percentages derived from live counts. It updates automatically as WebSocket messages arrive.

---

## API Endpoints Used

```
GET /spots/available?lat=-6.2088&lng=106.8456&radius=5000   → SpotModel[]
GET /analytics/occupancy?date=<selected>                    → OccupancyResponse  (refetch 30s)
WS  /ws/availability                                        → SpotStateChangedEvent stream
```

---

## Marker Colours

| State | Colour |
|---|---|
| `FREE` | `#22c55e` (green-500) |
| `RESERVED` | `#f59e0b` (amber-400) |
| `OCCUPIED` | `#ef4444` (red-500) |

---

## How to Test

1. Start the backend — particularly the sensor simulator and availability service.
2. Open the Occupancy page — spots should appear on the map.
3. Trigger a reservation on the mobile app (or via API) — the affected spot's marker should turn amber, then red, in real time without refreshing the page.
