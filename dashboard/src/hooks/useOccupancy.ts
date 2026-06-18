import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/axios';
import type { OccupancyResponse } from '../types/api';

export function useOccupancy(date: string) {
  return useQuery({
    queryKey: ['occupancy', date],
    queryFn: (): Promise<OccupancyResponse> =>
      api.get(`/analytics/occupancy?date=${date}`).then((r) => r.data),
    refetchInterval: 30_000,
  });
}
