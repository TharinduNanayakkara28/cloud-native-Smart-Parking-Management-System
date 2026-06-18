import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
  Legend,
} from 'recharts';
import type { EventRecord } from '../../types/api';
import { format, subDays } from 'date-fns';

function buildDailyData(events: EventRecord[]) {
  const buckets: Record<string, number> = {};
  for (let i = 6; i >= 0; i--) {
    buckets[format(subDays(new Date(), i), 'yyyy-MM-dd')] = 0;
  }
  for (const e of events) {
    const day = e.eventTime.slice(0, 10);
    if (day in buckets) {
      buckets[day] += e.amount ?? 0;
    }
  }
  return Object.entries(buckets).map(([date, net]) => ({
    day: format(new Date(date), 'EEE'),
    net: Math.round(net * 100) / 100,
  }));
}

interface Props {
  events?: EventRecord[];
  loading?: boolean;
}

export default function RevenueChart({ events, loading }: Props) {
  if (loading)
    return <div className="h-44 bg-gray-50 rounded-lg animate-pulse" />;

  const data = buildDailyData(events ?? []);

  return (
    <ResponsiveContainer width="100%" height={176}>
      <LineChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
        <XAxis dataKey="day" tick={{ fontSize: 11 }} tickLine={false} />
        <YAxis
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          tickFormatter={(v) => `$${v}`}
        />
        <Tooltip formatter={(v) => [`$${v}`, 'Net Revenue']} />
        <Line
          type="monotone"
          dataKey="net"
          stroke="#3b82f6"
          strokeWidth={2}
          dot={false}
          name="Net"
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
