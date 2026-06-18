import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/axios';
import type { ViolationsResponse } from '../types/api';

export function useViolations() {
  return useQuery({
    queryKey: ['violations'],
    queryFn: (): Promise<ViolationsResponse> =>
      api.get('/analytics/violations').then((r) => r.data),
    refetchInterval: 60_000,
  });
}
