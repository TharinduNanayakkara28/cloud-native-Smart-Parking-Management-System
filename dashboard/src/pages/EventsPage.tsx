import { useState } from 'react';
import { useAnalyticsEvents } from '../hooks/useAnalyticsEvents';
import EventsTable from '../components/events/EventsTable';
import RefreshButton from '../components/shared/RefreshButton';
import LoadingSpinner from '../components/shared/LoadingSpinner';
import ErrorAlert from '../components/shared/ErrorAlert';

const EVENT_TYPES = [
  'all',
  'reservation.created',
  'reservation.active',
  'reservation.completed',
  'reservation.cancelled',
  'reservation.expired',
  'payment.captured',
  'payment.refunded',
  'payment.failed',
  'penalty.issued',
  'spot.state.changed',
];

const LIMITS = [50, 100, 200] as const;

export default function EventsPage() {
  const [eventType, setEventType] = useState('all');
  const [limit, setLimit] = useState<(typeof LIMITS)[number]>(50);

  const {
    data: events,
    isLoading,
    error,
    refetch,
  } = useAnalyticsEvents({
    type: eventType === 'all' ? undefined : eventType,
    limit,
  });

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Event Log</h1>
        <RefreshButton onRefresh={refetch} />
      </div>

      {error && <ErrorAlert message="Failed to load events." />}

      {/* Filters */}
      <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center gap-4">
        <div className="flex items-center gap-2">
          <label className="text-sm font-medium text-gray-700">Type</label>
          <select
            value={eventType}
            onChange={(e) => setEventType(e.target.value)}
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t === 'all' ? 'All events' : t}
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2">
          <label className="text-sm font-medium text-gray-700">Show</label>
          <select
            value={limit}
            onChange={(e) =>
              setLimit(Number(e.target.value) as (typeof LIMITS)[number])
            }
            className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            {LIMITS.map((l) => (
              <option key={l} value={l}>
                {l} events
              </option>
            ))}
          </select>
        </div>

        <div className="flex gap-4 ml-auto text-xs text-gray-500">
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-blue-400" />
            Reservations
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-green-400" />
            Payments
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-red-400" />
            Penalties
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-gray-400" />
            Spots
          </span>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        {isLoading ? (
          <LoadingSpinner />
        ) : (
          <>
            <p className="text-xs text-gray-400 mb-4">
              Showing {events?.length ?? 0} events
            </p>
            <EventsTable events={events ?? []} />
          </>
        )}
      </div>
    </div>
  );
}
