import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/axios';
import type { SpotModel } from '../types/api';

const DEFAULT_LAT = -6.2088;
const DEFAULT_LNG = 106.8456;

export function useSpots(lat = DEFAULT_LAT, lng = DEFAULT_LNG, radius = 5000) {
  return useQuery({
    queryKey: ['spots', lat, lng, radius],
    queryFn: (): Promise<SpotModel[]> =>
      api
        .get(`/spots/available?lat=${lat}&lng=${lng}&radius=${radius}`)
        .then((r) => r.data),
  });
}
