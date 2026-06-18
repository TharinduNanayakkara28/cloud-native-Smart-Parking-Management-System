# Dashboard Phase D — Revenue Page

## What Was Built

A dedicated revenue analysis page with a week/month period toggle, four KPI stat cards, a dual-line chart showing gross vs net revenue per day, and a recent transactions table — all derived from real event data.

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/src/components/revenue/PeriodSelector.tsx` | Toggle between "This Week" and "This Month" — styled pill buttons |
| `dashboard/src/components/revenue/RevenueLineChart.tsx` | Recharts `LineChart` with two lines (Gross blue, Net green); derives per-day data from `payment.captured` and `payment.refunded` events |
| `dashboard/src/pages/RevenuePage.tsx` | Composes period selector, KPI strip, line chart, and recent transactions table |

---

## Page Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Revenue                        [This Week] [This Month]     │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────┐ ┌───────────┐ ┌──────────┐ ┌─────────────┐  │
│  │   Gross    │ │ Refunded  │ │   Net    │ │Transactions │  │
│  │  $150.00   │ │  $20.00   │ │ $130.00  │ │     35      │  │
│  └────────────┘ └───────────┘ └──────────┘ └─────────────┘  │
├──────────────────────────────────────────────────────────────┤
│  Gross vs Net Revenue — Daily Breakdown                      │
│                                                              │
│  $160 │       ●  ──────────────────── Gross (blue)          │
│  $140 │    ●──●                                              │
│  $120 │──●           ●──●──● ──────── Net (green)           │
│       └──────────────────────────────                        │
│        Mon  Tue  Wed  Thu  Fri  Sat  Sun                     │
├──────────────────────────────────────────────────────────────┤
│  Recent Transactions                                         │
│  Time | Event | Entity ID | Amount                           │
│  (latest 20 payment.captured events)                         │
└──────────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

**Daily chart from raw events**  
`RevenueLineChart` receives `capturedEvents` and `refundedEvents` arrays. It builds a date-keyed bucket map for the last N days and sums amounts per day:

```ts
function buildDailyRevenue(captured, refunded, days) {
  const buckets = {};
  for (let i = days - 1; i >= 0; i--) {
    buckets[format(subDays(new Date(), i), 'yyyy-MM-dd')] = { gross: 0, refunded: 0 };
  }
  for (const e of captured) buckets[e.eventTime.slice(0, 10)]?.gross += e.amount ?? 0;
  for (const e of refunded) buckets[e.eventTime.slice(0, 10)]?.refunded += e.amount ?? 0;
  return Object.entries(buckets).map(([date, { gross, refunded }]) => ({
    date: format(new Date(date), 'MMM d'),
    gross,
    net: gross - refunded,
  }));
}
```

The `/analytics/revenue` aggregate endpoint is used only for the four KPI cards (it returns pre-computed totals). It is not used for the daily breakdown chart.

**Period selector drives both KPIs and chart**  
`period` state is `'week' | 'month'`. It passes to `useRevenue(period)` (KPI cards) and sets `days = 7 | 30` (chart bucket count). Both update together on toggle — no separate state.

**Transactions table shows real rows**  
The breakdown table renders the latest 20 `payment.captured` events directly. Each row shows timestamp, event type, truncated entity ID, and amount. This is more actionable than an aggregate table with one row per day.

---

## API Endpoints Used

```
GET /analytics/revenue?period=week               → RevenueResponse  (refetch 60s)
GET /analytics/revenue?period=month              → RevenueResponse  (refetch 60s)
GET /analytics/events?type=payment.captured&limit=200  → EventRecord[]
GET /analytics/events?type=payment.refunded&limit=50   → EventRecord[]
```

---

## How to Test

1. Open Revenue page — KPI cards should show week totals.
2. Toggle to "This Month" — KPI cards update; chart extends to 30 days.
3. With payment activity in the system, the line chart should show non-zero values on active days.
4. The transactions table lists individual payment events newest-first.
