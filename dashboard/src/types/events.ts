export interface SpotStateChangedPayload {
  spotId: string;
  state: 'FREE' | 'RESERVED' | 'OCCUPIED';
  spotNumber?: string;
  lastUpdated?: string;
}

export interface SpotStateChangedEvent {
  eventType: 'spot.state.changed';
  payload: SpotStateChangedPayload;
}

export type WsMessage = SpotStateChangedEvent | { eventType: string; payload: unknown };
