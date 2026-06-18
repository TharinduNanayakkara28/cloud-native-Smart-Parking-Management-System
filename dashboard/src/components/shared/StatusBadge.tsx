type Variant = 'success' | 'warning' | 'error' | 'info' | 'neutral';

const STYLES: Record<Variant, string> = {
  success: 'bg-green-100 text-green-700',
  warning: 'bg-amber-100 text-amber-700',
  error: 'bg-red-100 text-red-700',
  info: 'bg-blue-100 text-blue-700',
  neutral: 'bg-gray-100 text-gray-600',
};

interface Props {
  label: string;
  variant?: Variant;
}

export default function StatusBadge({ label, variant = 'neutral' }: Props) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STYLES[variant]}`}
    >
      {label}
    </span>
  );
}
