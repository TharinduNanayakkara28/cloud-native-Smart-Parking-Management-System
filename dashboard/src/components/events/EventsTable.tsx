import { format } from 'date-fns';
import type { EventRecord } from '../../types/api';

function topicRowClass(topic: string): string {
  if (topic.includes('reservation')) return 'hover:bg-blue-50/60';
  if (topic.includes('payment')) return 'hover:bg-green-50/60';
  if (topic.includes('penalty')) return 'hover:bg-red-50/60';
  if (topic.includes('spot') || topic.includes('sensor'))
    return 'hover:bg-gray-50/60';
  return 'hover:bg-gray-50/60';
}

function topicDot(topic: string): string {
  if (topic.includes('reservation')) return 'bg-blue-400';
  if (topic.includes('payment')) return 'bg-green-400';
  if (topic.includes('penalty')) return 'bg-red-400';
  if (topic.includes('spot') || topic.includes('sensor')) return 'bg-gray-400';
  return 'bg-gray-300';
}

interface Props {
  events: EventRecord[];
  mini?: boolean;
}

export default function EventsTable({ events, mini }: Props) {
  if (!events.length) {
    return (
      <p className="text-sm text-gray-400 py-8 text-center">No events found</p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-200 text-left">
            <th className="pb-3 font-medium text-gray-500 pr-4">Time</th>
            <th className="pb-3 font-medium text-gray-500 pr-4">Event</th>
            {!mini && (
              <>
                <th className="pb-3 font-medium text-gray-500 pr-4">Topic</th>
                <th className="pb-3 font-medium text-gray-500 pr-4">
                  User ID
                </th>
                <th className="pb-3 font-medium text-gray-500 pr-4">
                  Entity ID
                </th>
              </>
            )}
            <th className="pb-3 font-medium text-gray-500 text-right">
              Amount
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {events.map((e) => (
            <tr
              key={e.id}
              className={`transition-colors ${topicRowClass(e.topic)}`}
            >
              <td className="py-2.5 pr-4 text-gray-500 whitespace-nowrap text-xs">
                {format(new Date(e.eventTime), 'MMM d, HH:mm:ss')}
              </td>
              <td className="py-2.5 pr-4">
                <div className="flex items-center gap-2">
                  <span
                    className={`w-2 h-2 rounded-full shrink-0 ${topicDot(e.topic)}`}
                  />
                  <span className="font-mono text-xs text-gray-700">
                    {e.eventType}
                  </span>
                </div>
              </td>
              {!mini && (
                <>
                  <td className="py-2.5 pr-4 text-xs text-gray-500">
                    {e.topic}
                  </td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-gray-400">
                    {e.userId ? e.userId.slice(0, 8) + '…' : '—'}
                  </td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-gray-400">
                    {e.entityId ? e.entityId.slice(0, 8) + '…' : '—'}
                  </td>
                </>
              )}
              <td className="py-2.5 text-right font-medium text-gray-900 text-xs">
                {e.amount != null ? `$${e.amount.toFixed(2)}` : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
