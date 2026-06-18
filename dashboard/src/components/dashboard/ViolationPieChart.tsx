import {
  PieChart,
  Pie,
  Cell,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import type { ViolationsResponse } from '../../types/api';

const TIER_COLORS = ['#9ca3af', '#f59e0b', '#ef4444'];
const TIER_LABELS = ['Warning', 'Fine', 'Escalated'];

interface Props {
  data?: ViolationsResponse;
  loading?: boolean;
}

export default function ViolationPieChart({ data, loading }: Props) {
  if (loading)
    return <div className="h-44 bg-gray-50 rounded-lg animate-pulse" />;

  if (!data?.byTier?.length) {
    return (
      <div className="h-44 flex items-center justify-center text-sm text-gray-400">
        No violations recorded
      </div>
    );
  }

  const chartData = data.byTier.map((t) => ({
    name: TIER_LABELS[t.tier - 1] ?? t.type,
    value: t.count,
  }));

  return (
    <ResponsiveContainer width="100%" height={176}>
      <PieChart>
        <Pie
          data={chartData}
          cx="50%"
          cy="50%"
          innerRadius={40}
          outerRadius={70}
          dataKey="value"
          paddingAngle={2}
        >
          {chartData.map((_, i) => (
            <Cell
              key={i}
              fill={TIER_COLORS[i % TIER_COLORS.length]}
            />
          ))}
        </Pie>
        <Tooltip formatter={(v, name) => [v, name]} />
        <Legend iconType="circle" iconSize={8} />
      </PieChart>
    </ResponsiveContainer>
  );
}
