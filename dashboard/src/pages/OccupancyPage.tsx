import { useState, useCallback } from 'react';
import { format } from 'date-fns';
import { useSpots } from '../hooks/useSpots';
import { useOccupancy } from '../hooks/useOccupancy';
import { useSpotUpdates } from '../hooks/useSpotUpdates';
import SpotMap from '../components/occupancy/SpotMap';
import HourlyBarChart from '../components/occupancy/HourlyBarChart';
import DatePicker from '../components/shared/DatePicker';
import LoadingSpinner from '../components/shared/LoadingSpinner';
import ErrorAlert from '../components/shared/ErrorAlert';
import type { SpotModel } from '../types/api';

const today = format(new Date(), 'yyyy-MM-dd');

export default function OccupancyPage() {
  const [date, setDate] = useState(today);
  const [spotStates, setSpotStates] = useState<
    Record<string, SpotModel['state']>
  >({});

  const { data: rawSpots, isLoading: spotsLoading, error: spotsErr } = useSpots();
  const { data: occupancy, isLoading: occLoading } = useOccupancy(date);

  const handleSpotUpdate = useCallback(
    (spotId: string, state: SpotModel['state']) => {
      setSpotStates((prev) => ({ ...prev, [spotId]: state }));
    },
    []
  );

  useSpotUpdates(handleSpotUpdate);

  const spots: SpotModel[] = (rawSpots ?? []).map((s) => ({
    ...s,
    state: spotStates[s.id] ?? s.state,
  }));

  const freeCount = spots.filter((s) => s.state === 'FREE').length;
  const reservedCount = spots.filter((s) => s.state === 'RESERVED').length;
  const occupiedCount = spots.filter((s) => s.state === 'OCCUPIED').length;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Live Occupancy</h1>
        <div className="flex items-center gap-4">
          <DatePicker
            label="Date"
            value={date}
            onChange={setDate}
            max={today}
          />
          <span className="flex items-center gap-1.5 text-xs text-green-600 font-medium">
            <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
            Live
          </span>
        </div>
      </div>

      {spotsErr && (
        <ErrorAlert message="Could not load spots. Is the backend running?" />
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Map */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 overflow-hidden">
          {spotsLoading ? (
            <LoadingSpinner className="h-[420px]" />
          ) : (
            <div className="h-[420px]">
              <SpotMap spots={spots} />
            </div>
          )}
          {/* Legend */}
          <div className="flex items-center gap-6 px-4 py-3 border-t border-gray-100 text-xs text-gray-600">
            {[
              { color: '#22c55e', label: 'Free' },
              { color: '#f59e0b', label: 'Reserved' },
              { color: '#ef4444', label: 'Occupied' },
            ].map(({ color, label }) => (
              <div key={label} className="flex items-center gap-1.5">
                <span
                  className="w-3 h-3 rounded-full"
                  style={{ background: color }}
                />
                {label}
              </div>
            ))}
          </div>
        </div>

        {/* Summary sidebar */}
        <div className="space-y-4">
          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">
              Current Status
            </h3>
            <div className="space-y-3">
              {[
                { label: 'Total Spots', value: spots.length, color: 'text-gray-900' },
                { label: 'Free', value: freeCount, color: 'text-green-600' },
                { label: 'Reserved', value: reservedCount, color: 'text-amber-600' },
                { label: 'Occupied', value: occupiedCount, color: 'text-red-600' },
              ].map(({ label, value, color }) => (
                <div key={label} className="flex justify-between items-center">
                  <span className="text-sm text-gray-500">{label}</span>
                  <span className={`text-lg font-bold ${color}`}>{value}</span>
                </div>
              ))}
            </div>

            {spots.length > 0 && (
              <div className="mt-4 pt-4 border-t border-gray-100">
                <div className="flex h-2 rounded-full overflow-hidden gap-0.5">
                  <div
                    className="bg-green-400 transition-all"
                    style={{ width: `${(freeCount / spots.length) * 100}%` }}
                  />
                  <div
                    className="bg-amber-400 transition-all"
                    style={{ width: `${(reservedCount / spots.length) * 100}%` }}
                  />
                  <div
                    className="bg-red-400 transition-all"
                    style={{ width: `${(occupiedCount / spots.length) * 100}%` }}
                  />
                </div>
                <p className="text-xs text-gray-400 mt-2">
                  {spots.length > 0
                    ? `${Math.round((occupiedCount / spots.length) * 100)}% occupied`
                    : '0% occupied'}
                </p>
              </div>
            )}
          </div>

          <div className="bg-white rounded-xl border border-gray-200 p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">
              Hourly Reservations
            </h3>
            <HourlyBarChart
              data={occupancy?.hourlyBreakdown}
              loading={occLoading}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
