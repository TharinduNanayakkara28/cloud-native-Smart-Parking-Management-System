import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { format, subDays } from 'date-fns';
import type { EventRecord } from '../../types/api';

function buildDailyRevenue(
  capturedEvents: EventRecord[],
  refundedEvents: EventRecord[],
  days: number
) {
  const buckets: Record<string, { gross: number; refunded: number }> = {};
  for (let i = days - 1; i >= 0; i--) {
    buckets[format(subDays(new Date(), i), 'yyyy-MM-dd')] = {
      gross: 0,
      refunded: 0,
    };
  }

  for (const e of capturedEvents) {
    const day = e.eventTime.slice(0, 10);
    if (day in buckets) buckets[day].gross += e.amount ?? 0;
  }
  for (const e of refundedEvents) {
    const day = e.eventTime.slice(0, 10);
    if (day in buckets) buckets[day].refunded += e.amount ?? 0;
  }

  return Object.entries(buckets).map(([date, { gross, refunded }]) => ({
    date: format(new Date(date), 'MMM d'),
    gross: Math.round(gross * 100) / 100,
    net: Math.round((gross - refunded) * 100) / 100,
  }));
}

interface Props {
  capturedEvents?: EventRecord[];
  refundedEvents?: EventRecord[];
  days?: number;
  loading?: boolean;
}

export default function RevenueLineChart({
  capturedEvents = [],
  refundedEvents = [],
  days = 7,
  loading,
}: Props) {
  if (loading)
    return <div className="h-72 bg-gray-50 rounded-lg animate-pulse" />;

  const data = buildDailyRevenue(capturedEvents, refundedEvents, days);

  return (
    <ResponsiveContainer width="100%" height={288}>
      <LineChart data={data} margin={{ top: 5, right: 20, bottom: 5, left: 10 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
        <XAxis dataKey="date" tick={{ fontSize: 11 }} tickLine={false} />
        <YAxis
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => `$${v}`}
        />
        <Tooltip formatter={(v) => [`$${v}`, '']} />
        <Legend iconType="circle" iconSize={8} />
        <Line
          type="monotone"
          dataKey="gross"
          stroke="#3b82f6"
          strokeWidth={2}
          dot={false}
          name="Gross"
        />
        <Line
          type="monotone"
          dataKey="net"
          stroke="#10b981"
          strokeWidth={2}
          dot={false}
          name="Net"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
