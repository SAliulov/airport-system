import { useCallback } from 'react';
import { useStomp } from './useStomp';
import type { DelayWarningPush, FlightRs, FlightStatusPush, GateAssignmentPush } from '../types';
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
          const flight = msg as unknown as FlightStatusPush & FlightRs;
          if (!flight.flightId) return;
          highlight(flight.flightId);
          setFlights(prev =>
            prev.map(f =>
              f.flightId === flight.flightId
                ? {
                    ...f,
                    status: flight.status ?? f.status,
                    actualDeparture: flight.actualDeparture ?? f.actualDeparture,
                    actualArrival: flight.actualArrival ?? f.actualArrival,
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

  useStomp([...BOARD_WS_TOPICS], handleMessage);
}
