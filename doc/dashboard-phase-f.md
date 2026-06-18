# Dashboard Phase F — Event Log Page

## What Was Built

A raw event log for debugging and auditing the entire event pipeline — every Kafka message that passed through the analytics service is browsable here, filterable by event type, with colour-coded rows by topic and manual refresh.

---

## Files Created

| File | Purpose |
|---|---|
| `dashboard/src/components/events/EventsTable.tsx` | Topic colour-coded table — rows highlight blue/green/red/grey by event topic; `mini` prop for compact dashboard widget view |
| `dashboard/src/pages/EventsPage.tsx` | Event type dropdown, limit selector (50/100/200), topic colour legend, refresh button, full-width `EventsTable` |

---

## Page Layout

```
┌──────────────────────────────────────────────────────────────┐
│  Event Log                                       [Refresh ↺] │
├──────────────────────────────────────────────────────────────┤
│  Type: [All events ▼]   Show: [50 events ▼]                  │
│                                ● Reservations  ● Payments     │
│                                ● Penalties     ● Spots        │
├──────────────────────────────────────────────────────────────┤
│  Showing 50 events                                           │
│                                                              │
│  Time         | Event Type             | Amount              │
│  Jun 18 14:32 | ● payment.captured     | $4.00               │
│  Jun 18 14:30 | ● reservation.active   | —                   │
│  Jun 18 14:20 | ● reservation.created  | —                   │
│  Jun 18 12:05 | ● penalty.issued       | $25.00              │
│  Jun 18 09:55 | ● spot.state.changed   | —                   │
│  …                                                           │
└──────────────────────────────────────────────────────────────┘
```

---

## Topic Row Colour Coding

| Topic contains | Row hover | Dot colour |
|---|---|---|
| `reservation` | `hover:bg-blue-50/60` | `bg-blue-400` |
| `payment` | `hover:bg-green-50/60` | `bg-green-400` |
| `penalty` | `hover:bg-red-50/60` | `bg-red-400` |
| `spot` / `sensor` | `hover:bg-gray-50/60` | `bg-gray-400` |

---

## Supported Event Types (dropdown)

```
all
reservation.created
reservation.active
reservation.completed
reservation.cancelled
reservation.expired
payment.captured
payment.refunded
payment.failed
penalty.issued
spot.state.changed
```

---

## Key Design Decisions

**No auto-refetch**  
The events page is a manual audit tool, not a live feed. Auto-refetching would scroll the table and lose the operator's position. The `RefreshButton` gives explicit control.

**`mini` prop for reuse on Dashboard**  
`EventsTable` renders all columns by default (Time, Event Type, Topic, User ID, Entity ID, Amount). With `mini={true}` it hides Topic, User ID, and Entity ID — showing only the three most relevant columns for the compact dashboard widget, without duplicating the component.

**ID truncation**  
UUIDs are truncated to 8 characters + `…` for display: `entityId.slice(0, 8) + '…'`. The full ID is not needed for operational monitoring — it's available via the backend directly if the operator needs to investigate.

**Limit selector, not pagination**  
Rather than paginated controls, the page offers three snapshot sizes (50/100/200). This is simpler to implement and appropriate for the operational monitoring use case where operators scan for anomalies rather than browse historical data exhaustively.

---

## API Endpoints Used

```
GET /analytics/events?type=<filter>&limit=<50|100|200>   → EventRecord[]
```

---

## How to Test

1. Open Events page — should immediately show the 50 most recent events across all types.
2. Select `payment.captured` from the type dropdown — only payment events appear.
3. Change limit to 200 — more events load.
4. Click Refresh — table reloads with the latest events.
5. Verify row colours: payment events have a green dot and green hover; penalty events have red.
