import { format } from 'date-fns';
import { DollarSign, BookOpen, AlertTriangle, Activity } from 'lucide-react';
import { useRevenue } from '../hooks/useRevenue';
import { useViolations } from '../hooks/useViolations';
import { useOccupancy } from '../hooks/useOccupancy';
import { useAnalyticsEvents } from '../hooks/useAnalyticsEvents';
import KpiCard from '../components/dashboard/KpiCard';
import RevenueChart from '../components/dashboard/RevenueChart';
import ViolationPieChart from '../components/dashboard/ViolationPieChart';
import HourlyBarChart from '../components/occupancy/HourlyBarChart';
import EventsTable from '../components/events/EventsTable';
import ErrorAlert from '../components/shared/ErrorAlert';

const today = format(new Date(), 'yyyy-MM-dd');

export default function DashboardPage() {
  const { data: revenue, isLoading: revLoading, error: revErr } = useRevenue('week');
  const { data: violations, isLoading: violLoading } = useViolations();
  const { data: occupancy, isLoading: occLoading } = useOccupancy(today);
  const { data: recentEvents } = useAnalyticsEvents({ limit: 10 });
  const { data: paymentEvents } = useAnalyticsEvents({
    type: 'payment.captured',
    limit: 50,
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Overview</h1>
        <span className="text-sm text-gray-500">
          {format(new Date(), 'EEEE, d MMMM yyyy')}
        </span>
      </div>

      {revErr && (
        <ErrorAlert message="Failed to load analytics. Is the backend running?" />
      )}

      {/* KPI Row */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          title="Net Revenue (week)"
          value={
            revenue ? `$${revenue.netRevenue.toFixed(2)}` : '—'
          }
          subtitle={`${revenue?.transactionCount ?? 0} transactions`}
          icon={<DollarSign size={18} />}
          loading={revLoading}
        />
        <KpiCard
          title="Reservations Today"
          value={occupancy?.totalReservations ?? '—'}
          subtitle="Check-ins + active"
          icon={<BookOpen size={18} />}
          loading={occLoading}
        />
        <KpiCard
          title="Total Violations"
          value={violations?.totalViolations ?? '—'}
          subtitle="All time"
          icon={<AlertTriangle size={18} />}
          loading={violLoading}
        />
        <KpiCard
          title="Gross Revenue (week)"
          value={
            revenue ? `$${revenue.grossRevenue.toFixed(2)}` : '—'
          }
          subtitle={`$${revenue?.refundedAmount.toFixed(2) ?? '0.00'} refunded`}
          icon={<Activity size={18} />}
          loading={revLoading}
        />
      </div>

      {/* Charts row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Revenue — This Week
          </h2>
          <RevenueChart events={paymentEvents} loading={revLoading} />
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <h2 className="text-sm font-semibold text-gray-700 mb-4">
            Violation Breakdown
          </h2>
          <ViolationPieChart data={violations} loading={violLoading} />
        </div>
      </div>

      {/* Hourly occupancy */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">
          Today's Hourly Reservations
        </h2>
        <HourlyBarChart
          data={occupancy?.hourlyBreakdown}
          loading={occLoading}
        />
      </div>

      {/* Recent events */}
      <div className="bg-white rounded-xl border border-gray-200 p-5">
        <h2 className="text-sm font-semibold text-gray-700 mb-4">
          Recent Events
        </h2>
        <EventsTable events={recentEvents ?? []} mini />
      </div>
    </div>
  );
}
