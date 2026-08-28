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
  onOperationalEvent?: (message: string) => void;
}

/**
 * Подписка на STOMP-топики рейсов и частичный patch списка / модалки редактирования.
 */
export function useFlightWebSocket({
  setFlights,
  highlight,
  editingDetailRef,
  onDetailRefresh,
  onOperationalEvent,
}: FlightWebSocketOptions) {
  const handleWsMessage = useCallback(
    (body: string, topic: string) => {
      try {
        const msg = JSON.parse(body) as Record<string, unknown>;

        if (topic === '/topic/operational-events') {
          const message = (msg.message as string | undefined) ?? '';
          if (message && onOperationalEvent) {
            onOperationalEvent(message);
          }
          return;
        }

        if (topic === '/topic/flights' && msg.flightId) {
          const fid = Number(msg.flightId);
          const eventType = (msg._eventType as string | undefined) ?? 'UPDATED';

          if (eventType === 'DELETED') {
            setFlights(prev => prev.filter(f => f.flightId !== fid));
            if (onOperationalEvent) {
              onOperationalEvent(`Рейс #${fid} удалён`);
            }
            return;
          }

          if (eventType === 'CREATED') {
            const flightNumber = (msg as Record<string, unknown>).schedule
              ? ((msg as Record<string, unknown>).schedule as Record<string, unknown>)?.flightNumber as string ?? ''
              : '';
            const label = flightNumber || `#${fid}`;
            const status = (msg.status as string | undefined) ?? 'SCHEDULED';
            setFlights(prev => {
              if (prev.some(f => f.flightId === fid)) return prev;
              return [...prev, msg as unknown as FlightRs];
            });
            if (onOperationalEvent) {
              onOperationalEvent(`Создан рейс ${label} (#${fid}) — ${status}`);
            }
            return;
          }

          highlight(fid);
          setFlights(prev =>
            prev.map(fl =>
              fl.flightId === fid
                ? {
                    ...fl,
                    status: (msg.status as FlightStatus | undefined) ?? fl.status,
                    actualDeparture: (msg.actualDeparture as string | undefined) ?? fl.actualDeparture,
                    actualArrival: (msg.actualArrival as string | undefined) ?? fl.actualArrival,
                    aircraftType: (msg.aircraftType as FlightRs['aircraftType'] | undefined) ?? fl.aircraftType,
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
    [highlight, onDetailRefresh, setFlights, editingDetailRef, onOperationalEvent],
  );

  return useStomp([...FLIGHT_WS_TOPICS], handleWsMessage);
}
