import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/axios';
import type { EventRecord } from '../types/api';

interface Params {
  type?: string;
  limit?: number;
}

export function useAnalyticsEvents({ type, limit = 50 }: Params = {}) {
  return useQuery({
    queryKey: ['events', type ?? 'all', limit],
    queryFn: (): Promise<EventRecord[]> => {
      const params = new URLSearchParams({ limit: String(limit) });
      if (type) params.set('type', type);
      return api.get(`/analytics/events?${params.toString()}`).then((r) => r.data);
    },
  });
}
