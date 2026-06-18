# Dashboard Phase B — Overview Dashboard Page

## What Was Built

The main overview page operators see after login — four KPI cards pulling live analytics, a 7-day revenue mini-chart, a violation tier donut, a today's-hourly bar chart, and a recent event log. All sections auto-refetch in the background.

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/src/components/dashboard/KpiCard.tsx` | Metric card — title, large value, subtitle, optional lucide icon; Tailwind pulse skeleton while loading |
| `dashboard/src/components/dashboard/RevenueChart.tsx` | 7-day net revenue `LineChart` (Recharts) — derives per-day totals from `payment.captured` events |
| `dashboard/src/components/dashboard/ViolationPieChart.tsx` | Donut `PieChart` (Recharts) — three slices: Tier 1 grey / Tier 2 amber / Tier 3 red |
| `dashboard/src/pages/DashboardPage.tsx` | Composes all widgets; fetches revenue (week), violations, occupancy (today), recent events (limit 10), payment events (limit 50) |

---

## Page Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Overview                          Wednesday, 18 June 2025   │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────────┐ ┌──────────────┐ ┌──────────┐ ┌────────┐  │
│  │ Net Revenue  │ │ Reservations │ │Violations│ │ Gross  │  │
│  │ $130.00      │ │    42        │ │   12     │ │$150.00 │  │
│  │ 35 trans.    │ │ Check-ins… │ │ All time │ │$20 ref.│  │
│  └──────────────┘ └──────────────┘ └──────────┘ └────────┘  │
├──────────────────────┬───────────────────────────────────────┤
│  Revenue — This Week │  Violation Breakdown                  │
│  [LineChart]         │  [PieChart donut]                     │
│  (net, daily)        │  Warning / Fine / Escalated           │
├──────────────────────┴───────────────────────────────────────┤
│  Today's Hourly Reservations                                 │
│  [BarChart — hourlyBreakdown from /analytics/occupancy]      │
├──────────────────────────────────────────────────────────────┤
│  Recent Events (last 10, mini mode)                          │
│  Time | Event Type | Amount                                  │
└──────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Revenue chart from raw events, not aggregate**  
`RevenueChart` calls `useAnalyticsEvents({ type: 'payment.captured', limit: 50 })` and groups events by `eventTime.slice(0, 10)` to build a per-day net revenue series. The aggregate `/analytics/revenue` endpoint returns a single weekly/monthly total — not broken down by day — so raw events are the only way to draw a daily line chart.

**KPI card loading skeleton**  
While data is fetching, each `KpiCard` renders a `h-8 w-28 bg-gray-100 rounded-lg animate-pulse` placeholder in place of the value. The card's outer frame stays visible to preserve layout stability.

**Mini EventsTable**  
`EventsTable` accepts a `mini` prop. When true, it hides the Topic, User ID, and Entity ID columns, showing only Time, Event Type, and Amount — suitable for the compact dashboard widget.

---

## API Endpoints Used

```
GET /analytics/revenue?period=week           → RevenueResponse   (refetch 60s)
GET /analytics/violations                    → ViolationsResponse (refetch 60s)
GET /analytics/occupancy?date=<today>        → OccupancyResponse  (refetch 30s)
GET /analytics/events?limit=10               → EventRecord[]
GET /analytics/events?type=payment.captured&limit=50  → EventRecord[]
```

---

## How to Test

Start the app (`npm run dev`) and log in. The dashboard loads immediately. With the backend running and some parking activity:

- KPI cards should show non-zero values
- Revenue chart shows bars for days with `payment.captured` events
- Violation donut appears when any `penalty.issued` events exist
- Recent events table populates with the latest events across all topics
