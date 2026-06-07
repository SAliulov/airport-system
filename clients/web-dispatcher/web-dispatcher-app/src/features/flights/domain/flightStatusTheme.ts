import type { FlightStatus } from '../../../types';

/** Цвета статусов рейса для таблицы, модалки и timeline. */
export const FLIGHT_STATUS_COLORS: Record<FlightStatus, string> = {
  SCHEDULED: '#3b82f6',
  DEPARTED: '#f97316',
  ARRIVED: '#22c55e',
  DELAYED: '#ef4444',
  CANCELLED: '#6b7280',
};

/**
 * Возвращает CSS-цвет для статуса рейса.
 */
export function flightStatusColor(status: FlightStatus | string): string {
  return FLIGHT_STATUS_COLORS[status as FlightStatus] ?? '#3b82f6';
}
