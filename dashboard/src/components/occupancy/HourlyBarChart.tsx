import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
  CartesianGrid,
} from 'recharts';

interface HourlyEntry {
  hour: string;
  reservationCount: number;
}

interface Props {
  data?: HourlyEntry[];
  loading?: boolean;
}

export default function HourlyBarChart({ data, loading }: Props) {
  if (loading)
    return <div className="h-44 bg-gray-50 rounded-lg animate-pulse" />;

  if (!data?.length) {
    return (
      <div className="h-44 flex items-center justify-center text-sm text-gray-400">
        No data for selected date
      </div>
    );
  }

  return (
    <ResponsiveContainer width="100%" height={176}>
      <BarChart
        data={data}
        margin={{ top: 4, right: 8, bottom: 0, left: 0 }}
        barSize={18}
      >
        <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" vertical={false} />
        <XAxis
          dataKey="hour"
          tick={{ fontSize: 10 }}
          tickLine={false}
          interval="preserveStartEnd"
        />
        <YAxis
          tick={{ fontSize: 11 }}
          tickLine={false}
          axisLine={false}
          allowDecimals={false}
        />
        <Tooltip
          formatter={(v) => [v, 'Reservations']}
          labelFormatter={(l) => `Hour: ${l}`}
        />
        <Bar dataKey="reservationCount" fill="#3b82f6" radius={[3, 3, 0, 0]} name="Reservations" />
      </BarChart>
    </ResponsiveContainer>
  );
}
