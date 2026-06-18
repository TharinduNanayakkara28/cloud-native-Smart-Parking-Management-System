import { format } from 'date-fns';
import type { EventRecord } from '../../types/api';
import TierBadge from './TierBadge';

interface Props {
  events: EventRecord[];
  tierFilter: number | null;
}

export default function ViolationsTable({ events, tierFilter }: Props) {
  const filtered =
    tierFilter === null
      ? events
      : events.filter((e) => e.tier === tierFilter);

  if (!filtered.length) {
    return (
      <p className="text-sm text-gray-400 py-8 text-center">
        No violations match the selected filter
      </p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-200 text-left">
            <th className="pb-3 font-medium text-gray-500 pr-4">Issued At</th>
            <th className="pb-3 font-medium text-gray-500 pr-4">Entity ID</th>
            <th className="pb-3 font-medium text-gray-500 pr-4">Tier</th>
            <th className="pb-3 font-medium text-gray-500 pr-4">Type</th>
            <th className="pb-3 font-medium text-gray-500 text-right">
              Amount
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {filtered.map((e) => (
            <tr key={e.id} className="hover:bg-gray-50 transition-colors">
              <td className="py-3 pr-4 text-gray-600 whitespace-nowrap">
                {format(new Date(e.eventTime), 'MMM d, HH:mm')}
              </td>
              <td className="py-3 pr-4 font-mono text-xs text-gray-500">
                {e.entityId ? e.entityId.slice(0, 8) + '…' : '—'}
              </td>
              <td className="py-3 pr-4">
                {e.tier !== null ? <TierBadge tier={e.tier} /> : '—'}
              </td>
              <td className="py-3 pr-4 text-gray-600">{e.eventType}</td>
              <td className="py-3 text-right font-medium text-gray-900">
                {e.amount != null ? `$${e.amount.toFixed(2)}` : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
