import { useState } from 'react';
import { useRevenue } from '../hooks/useRevenue';
import { useAnalyticsEvents } from '../hooks/useAnalyticsEvents';
import PeriodSelector from '../components/revenue/PeriodSelector';
import RevenueLineChart from '../components/revenue/RevenueLineChart';
import ErrorAlert from '../components/shared/ErrorAlert';

function StatCard({
  label,
  value,
  loading,
}: {
  label: string;
  value: string;
  loading: boolean;
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5">
      <p className="text-sm text-gray-500 mb-2">{label}</p>
      {loading ? (
        <div className="h-7 w-24 bg-gray-100 rounded animate-pulse" />
      ) : (
        <p className="text-xl font-bold text-gray-900">{value}</p>
      )}
    </div>
  );
}

export default function RevenuePage() {
  const [period, setPeriod] = useState<'week' | 'month'>('week');

  const { data: revenue, isLoading: revLoading, error } = useRevenue(period);
  const days = period === 'week' ? 7 : 30;

  const { data: captured, isLoading: captLoading } = useAnalyticsEvents({
    type: 'payment.captured',
    limit: 200,
  });
  const { data: refunded, isLoading: refLoading } = useAnalyticsEvents({
    type: 'payment.refunded',
    limit: 50,
  });

  const chartLoading = captLoading || refLoading;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Revenue</h1>
        <PeriodSelector value={period} onChange={setPeriod} />
      </div>

      {error && <ErrorAlert message="Failed to load revenue data." />}

      {/* KPI strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          label="Gross Revenue"
          value={`$${revenue?.grossRevenue.toFixed(2) ?? '—'}`}
          loading={revLoading}
        />
        <StatCard
          label="Refunded"
          value={`$${revenue?.refundedAmount.toFixed(2) ?? '—'}`}
          loading={revLoading}
        />
        <StatCard
          label="Net Revenue"
          value={`$${revenue?.netRevenue.toFixed(2) ?? '—'}`}
          loading={revLoading}
        />
        <StatCard
          label="Transactions"
          value={String(revenue?.transactionCount ?? '—')}
          loading={revLoading}
        />
      </div>

      {/* Line chart */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">
          Gross vs Net Revenue — Daily Breakdown
        </h2>
        <RevenueLineChart
          capturedEvents={captured}
          refundedEvents={refunded}
          days={days}
          loading={chartLoading}
        />
      </div>

      {/* Breakdown table */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">
          Recent Transactions
        </h2>
        {captLoading ? (
          <div className="space-y-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="h-8 bg-gray-100 rounded animate-pulse" />
            ))}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200 text-left">
                  <th className="pb-3 font-medium text-gray-500 pr-4">Time</th>
                  <th className="pb-3 font-medium text-gray-500 pr-4">
                    Event
                  </th>
                  <th className="pb-3 font-medium text-gray-500 pr-4">
                    Entity ID
                  </th>
                  <th className="pb-3 font-medium text-gray-500 text-right">
                    Amount
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(captured ?? []).slice(0, 20).map((e) => (
                  <tr key={e.id} className="hover:bg-gray-50">
                    <td className="py-2.5 pr-4 text-gray-500 text-xs whitespace-nowrap">
                      {new Date(e.eventTime).toLocaleString()}
                    </td>
                    <td className="py-2.5 pr-4">
                      <span className="font-mono text-xs text-gray-700">
                        {e.eventType}
                      </span>
                    </td>
                    <td className="py-2.5 pr-4 font-mono text-xs text-gray-400">
                      {e.entityId ? e.entityId.slice(0, 8) + '…' : '—'}
                    </td>
                    <td className="py-2.5 text-right font-semibold text-gray-900">
                      ${e.amount?.toFixed(2) ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
