import { useEffect, useRef } from 'react';

const WS_URL =
  import.meta.env.VITE_WS_URL ?? 'ws://localhost:8080/ws/availability';

export function useSpotUpdates(
  onUpdate: (spotId: string, state: 'FREE' | 'RESERVED' | 'OCCUPIED') => void
) {
  const onUpdateRef = useRef(onUpdate);
  onUpdateRef.current = onUpdate;

  useEffect(() => {
    let ws: WebSocket;
    let retryTimeout: ReturnType<typeof setTimeout>;

    function connect() {
      ws = new WebSocket(WS_URL);

      ws.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data as string);
          if (msg.eventType === 'spot.state.changed') {
            onUpdateRef.current(msg.payload.spotId, msg.payload.state);
          }
        } catch {
          // ignore malformed messages
        }
      };

      ws.onclose = () => {
        retryTimeout = setTimeout(connect, 3000);
      };
    }

    connect();

    return () => {
      clearTimeout(retryTimeout);
      ws?.close();
    };
  }, []);
}
