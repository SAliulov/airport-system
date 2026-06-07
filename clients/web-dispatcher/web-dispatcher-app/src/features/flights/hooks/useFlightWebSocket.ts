import { useCallback } from 'react';
import { getFlightById } from '../../../services/api';
import { useStomp } from '../../../hooks/useStomp';
import type { FlightRs, FlightStatus } from '../../../types';
import { FLIGHT_WS_TOPICS } from '../constants';

export interface FlightWebSocketOptions {
  setFlights: React.Dispatch<React.SetStateAction<FlightRs[]>>;
  highlight: (flightId: number) => void;
  editingDetailRef: React.MutableRefObject<FlightRs | null>;
  onDetailRefresh: (detail: FlightRs) => void;
}

/**
 * Подписка на STOMP-топики рейсов и частичный patch списка / модалки редактирования.
 */
export function useFlightWebSocket({
  setFlights,
  highlight,
  editingDetailRef,
  onDetailRefresh,
}: FlightWebSocketOptions) {
  const handleWsMessage = useCallback(
    (body: string, topic: string) => {
      try {
        const msg = JSON.parse(body) as Record<string, unknown>;
        if (topic === '/topic/flights' && msg.flightId) {
          const fid = Number(msg.flightId);
          highlight(fid);
          setFlights(prev =>
            prev.map(fl =>
              fl.flightId === fid
                ? {
                    ...fl,
                    status: (msg.status as FlightStatus | undefined) ?? fl.status,
                    actualDeparture: (msg.actualDeparture as string | undefined) ?? fl.actualDeparture,
                    actualArrival: (msg.actualArrival as string | undefined) ?? fl.actualArrival,
                  }
                : fl,
            ),
          );
        } else if (topic === '/topic/gate-changes' && msg.flightId) {
          const fid = Number(msg.flightId);
          highlight(fid);
          setFlights(prev =>
            prev.map(fl =>
              fl.flightId === fid
                ? {
                    ...fl,
                    currentGateAssignment:
                      (msg.assignment as FlightRs['currentGateAssignment']) ?? fl.currentGateAssignment,
                  }
                : fl,
            ),
          );
        } else if (topic === '/topic/delays' && msg.flightId) {
          highlight(Number(msg.flightId));
        }

        if (msg.flightId && editingDetailRef.current?.flightId === Number(msg.flightId)) {
          getFlightById(Number(msg.flightId))
            .then(onDetailRefresh)
            .catch(() => {});
        }
      } catch {
        // ignore malformed websocket payloads
      }
    },
    [highlight, onDetailRefresh, setFlights, editingDetailRef],
  );

  useStomp([...FLIGHT_WS_TOPICS], handleWsMessage);
}
