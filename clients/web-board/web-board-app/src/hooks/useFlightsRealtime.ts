import { useCallback } from 'react';
import { useStomp } from './useStomp';
import type { DelayWarningPush, FlightRs, FlightStatus, GateAssignmentPush } from '../types';
import { BOARD_WS_TOPICS } from '../constants/boardFilters';

export interface FlightsRealtimeOptions {
  setFlights: React.Dispatch<React.SetStateAction<FlightRs[]>>;
  highlight: (flightId: number) => void;
}

/** WebSocket patch списка рейсов на табло. */
export function useFlightsRealtime({ setFlights, highlight }: FlightsRealtimeOptions) {
  const handleMessage = useCallback(
    (body: string, topic: string) => {
      try {
        const msg = JSON.parse(body) as Record<string, unknown>;
        if (topic === '/topic/flights') {
          const eventType = (msg._eventType as string | undefined) ?? 'UPDATED';
          const fid = Number(msg.flightId);
          if (!fid) return;

          if (eventType === 'DELETED') {
            setFlights(prev => prev.filter(f => f.flightId !== fid));
            return;
          }

          if (eventType === 'CREATED') {
            setFlights(prev => {
              if (prev.some(f => f.flightId === fid)) return prev;
              return [...prev, msg as unknown as FlightRs];
            });
            return;
          }

          highlight(fid);
          setFlights(prev =>
            prev.map(f =>
              f.flightId === fid
                ? {
                    ...f,
                    status: (msg.status as FlightStatus) ?? f.status,
                    actualDeparture: (msg.actualDeparture as string | undefined) ?? f.actualDeparture,
                    actualArrival: (msg.actualArrival as string | undefined) ?? f.actualArrival,
                  }
                : f,
            ),
          );
        } else if (topic === '/topic/gate-changes') {
          const push = msg as unknown as GateAssignmentPush;
          if (!push.flightId) return;
          highlight(push.flightId);
          setFlights(prev =>
            prev.map(f =>
              f.flightId === push.flightId
                ? { ...f, currentGateAssignment: push.assignment }
                : f,
            ),
          );
        } else if (topic === '/topic/delays') {
          const push = msg as unknown as DelayWarningPush;
          if (!push.flightId) return;
          highlight(push.flightId);
        }
      } catch {
        // ignore malformed
      }
    },
    [highlight, setFlights],
  );

  return useStomp([...BOARD_WS_TOPICS], handleMessage);
}
