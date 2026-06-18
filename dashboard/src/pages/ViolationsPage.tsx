import { useState } from 'react';
import { useViolations } from '../hooks/useViolations';
import { useAnalyticsEvents } from '../hooks/useAnalyticsEvents';
import ViolationPieChart from '../components/dashboard/ViolationPieChart';
import ViolationsTable from '../components/violations/ViolationsTable';
import TierBadge from '../components/violations/TierBadge';
import LoadingSpinner from '../components/shared/LoadingSpinner';
import ErrorAlert from '../components/shared/ErrorAlert';
import RefreshButton from '../components/shared/RefreshButton';
import { queryClient } from '../lib/queryClient';

const TIERS = [
  { label: 'All', value: null },
  { label: 'Warning', value: 1 },
  { label: 'Fine', value: 2 },
  { label: 'Escalated', value: 3 },
];

export default function ViolationsPage() {
  const [tierFilter, setTierFilter] = useState<number | null>(null);

  const { data: violations, isLoading: violLoading, error: violErr } = useViolations();
  const {
    data: events,
    isLoading: eventsLoading,
    refetch,
  } = useAnalyticsEvents({ type: 'penalty.issued', limit: 100 });

  const byTier = violations?.byTier ?? [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Violations</h1>
        <RefreshButton
          onRefresh={async () => {
            await Promise.all([
              queryClient.invalidateQueries({ queryKey: ['violations'] }),
              refetch(),
            ]);
          }}
        />
      </div>

      {violErr && <ErrorAlert message="Failed to load violations data." />}

      {/* Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500 mb-2">Total</p>
          <p className="text-2xl font-bold text-gray-900">
            {violLoading ? '—' : (violations?.totalViolations ?? 0)}
          </p>
        </div>
        {byTier.map((t) => (
          <div
            key={t.tier}
            className="bg-white rounded-xl border border-gray-200 p-5"
          >
            <div className="flex items-center gap-2 mb-2">
              <TierBadge tier={t.tier} />
            </div>
            <p className="text-2xl font-bold text-gray-900">{t.count}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Pie chart */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Tier Breakdown
          </h2>
          <ViolationPieChart data={violations} loading={violLoading} />
        </div>

        {/* Table */}
        <div className="lg:col-span-3 bg-white rounded-xl border border-gray-200 p-5">
          <div className="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 className="text-sm font-semibold text-gray-700">
              Penalty Events
            </h2>
            {/* Tier filter */}
            <div className="flex gap-1">
              {TIERS.map(({ label, value }) => (
                <button
                  key={label}
                  onClick={() => setTierFilter(value)}
                  className={`px-3 py-1 rounded-full text-xs font-medium transition-colors ${
                    tierFilter === value
                      ? 'bg-gray-900 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>

          {eventsLoading ? (
            <LoadingSpinner />
          ) : (
            <ViolationsTable
              events={events ?? []}
              tierFilter={tierFilter}
            />
          )}
        </div>
      </div>
    </div>
  );
}
