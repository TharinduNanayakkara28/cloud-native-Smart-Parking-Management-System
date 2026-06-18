interface Props {
  value: 'week' | 'month';
  onChange: (v: 'week' | 'month') => void;
}

export default function PeriodSelector({ value, onChange }: Props) {
  return (
    <div className="flex bg-gray-100 rounded-lg p-1 gap-1">
      {(['week', 'month'] as const).map((p) => (
        <button
          key={p}
          onClick={() => onChange(p)}
          className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
            value === p
              ? 'bg-white text-gray-900 shadow-sm'
              : 'text-gray-500 hover:text-gray-700'
          }`}
        >
          {p === 'week' ? 'This Week' : 'This Month'}
        </button>
      ))}
    </div>
  );
}
