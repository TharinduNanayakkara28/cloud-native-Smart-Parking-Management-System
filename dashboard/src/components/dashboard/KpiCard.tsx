import { ReactNode } from 'react';

interface Props {
  title: string;
  value: string | number;
  subtitle?: string;
  icon?: ReactNode;
  loading?: boolean;
}

export default function KpiCard({
  title,
  value,
  subtitle,
  icon,
  loading,
}: Props) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-5 flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium text-gray-500">{title}</span>
        {icon && (
          <div className="w-9 h-9 bg-blue-50 rounded-lg flex items-center justify-center text-blue-600">
            {icon}
          </div>
        )}
      </div>
      {loading ? (
        <div className="h-8 w-28 bg-gray-100 rounded-lg animate-pulse" />
      ) : (
        <p className="text-2xl font-bold text-gray-900">{value}</p>
      )}
      {subtitle && !loading && (
        <p className="text-xs text-gray-400">{subtitle}</p>
      )}
    </div>
  );
}
