# Dashboard Phase E — Violations Page

## What Was Built

A violations monitoring page combining summary stat cards, a tier breakdown donut chart, and a filterable penalty events table — giving operators a single view of all issued penalties by severity.

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/src/components/violations/TierBadge.tsx` | Pill badge — Tier 1 grey / Tier 2 amber / Tier 3 red with descriptive label |
| `dashboard/src/components/violations/ViolationsTable.tsx` | Table of `penalty.issued` events — columns: Issued At, Entity ID, Tier, Type, Amount; client-side tier filter |
| `dashboard/src/pages/ViolationsPage.tsx` | Composes summary cards (total + per-tier), `ViolationPieChart`, `ViolationsTable` with tier filter buttons, `RefreshButton` |

---

## Page Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Violations                                      [Refresh ↺] │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────────┐  ┌──────────┐  ┌─────────┐  │
│  │  Total   │  │ [Warning]    │  │ [Fine]   │  │[Escal.] │  │
│  │    12    │  │     8        │  │    3     │  │    1    │  │
│  └──────────┘  └──────────────┘  └──────────┘  └─────────┘  │
├──────────────────┬───────────────────────────────────────────┤
│  Tier Breakdown  │  Penalty Events                           │
│  [PieChart]      │  Filter: [All] [Warning] [Fine] [Escalated│
│                  │                                           │
│  ● Warning (grey)│  Issued At  | Entity ID | Tier |  Amount  │
│  ● Fine (amber)  │  Jun 18 … | abc12345… | Fine  | $25.00   │
│  ● Escal. (red)  │  Jun 17 … | def67890… | Warn  | —        │
│                  │  …                                        │
└──────────────────┴───────────────────────────────────────────┘
```

---

## Tier Configuration

| Tier | Badge Label | Badge Colour | Pie Colour |
|---|---|---|---|
| 1 | Warning | `bg-gray-100 text-gray-600` | `#9ca3af` |
| 2 | Fine | `bg-amber-100 text-amber-700` | `#f59e0b` |
| 3 | Escalated | `bg-red-100 text-red-700` | `#ef4444` |

---

## Key Design Decisions

**Client-side tier filter**  
The table receives all `penalty.issued` events and applies `tierFilter` locally:

```ts
const filtered = tierFilter === null
  ? events
  : events.filter((e) => e.tier === tierFilter);
```

This avoids a re-fetch on every filter click. The limit is 100 events per load, which is sufficient for operational monitoring.

**Summary cards driven by `/analytics/violations`**  
The per-tier counts and total come from the pre-computed `ViolationsResponse.byTier` array, not from counting the raw events. This makes the total accurate over the full data set regardless of the event fetch limit.

**Shared `ViolationPieChart`**  
The same `ViolationPieChart` component is used on both the Dashboard overview and this page. The donut layout (`innerRadius={40}`) provides space to show the total count in the centre if needed in future.

**RefreshButton invalidates both queries**  
The `RefreshButton` on this page calls `queryClient.invalidateQueries({ queryKey: ['violations'] })` and `refetch()` (for the events query) simultaneously, ensuring both the summary and the table refresh together.

---

## API Endpoints Used

```
GET /analytics/violations                          → ViolationsResponse  (refetch 60s)
GET /analytics/events?type=penalty.issued&limit=100 → EventRecord[]
```

---

## How to Test

1. Trigger overstay violations via the mobile app or wait for the penalty service's scheduled job.
2. Open Violations page — summary cards and donut should reflect the penalty counts.
3. Click tier filter buttons — the table should update immediately without a network call.
4. Click Refresh — both the summary cards and table reload from the backend.
