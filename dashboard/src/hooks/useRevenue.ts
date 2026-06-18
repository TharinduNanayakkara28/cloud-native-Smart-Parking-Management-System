import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/axios';
import type { RevenueResponse } from '../types/api';

export function useRevenue(period: 'week' | 'month') {
  return useQuery({
    queryKey: ['revenue', period],
    queryFn: (): Promise<RevenueResponse> =>
      api.get(`/analytics/revenue?period=${period}`).then((r) => r.data),
    refetchInterval: 60_000,
  });
}
