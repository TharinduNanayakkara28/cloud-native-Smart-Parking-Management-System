import { MapContainer, TileLayer, CircleMarker, Tooltip } from 'react-leaflet';
import type { SpotModel } from '../../types/api';

const STATE_COLOR: Record<SpotModel['state'], string> = {
  FREE: '#22c55e',
  RESERVED: '#f59e0b',
  OCCUPIED: '#ef4444',
};

interface Props {
  spots: SpotModel[];
  center?: [number, number];
}

const DEFAULT_CENTER: [number, number] = [-6.2088, 106.8456];

export default function SpotMap({ spots, center = DEFAULT_CENTER }: Props) {
  return (
    <MapContainer
      center={center}
      zoom={17}
      className="h-full w-full rounded-lg z-0"
      scrollWheelZoom
    >
      <TileLayer
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      />
      {spots.map((spot) => (
        <CircleMarker
          key={spot.id}
          center={[spot.latitude, spot.longitude]}
          radius={10}
          fillColor={STATE_COLOR[spot.state]}
          color="white"
          weight={2}
          fillOpacity={0.9}
        >
          <Tooltip direction="top" offset={[0, -8]} opacity={0.95}>
            <div className="text-xs space-y-0.5">
              <p className="font-semibold">Spot {spot.spotNumber}</p>
              {spot.floor && <p>Floor: {spot.floor}</p>}
              <p>
                State:{' '}
                <span
                  style={{ color: STATE_COLOR[spot.state] }}
                  className="font-medium"
                >
                  {spot.state}
                </span>
              </p>
            </div>
          </Tooltip>
        </CircleMarker>
      ))}
    </MapContainer>
  );
}
