const TIER_CONFIG: Record<
  number,
  { label: string; className: string }
> = {
  1: { label: 'Warning', className: 'bg-gray-100 text-gray-600' },
  2: { label: 'Fine', className: 'bg-amber-100 text-amber-700' },
  3: { label: 'Escalated', className: 'bg-red-100 text-red-700' },
};

export default function TierBadge({ tier }: { tier: number }) {
  const config = TIER_CONFIG[tier] ?? {
    label: `Tier ${tier}`,
    className: 'bg-gray-100 text-gray-600',
  };

  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${config.className}`}
    >
      {config.label}
    </span>
  );
}
