import type { FlightStatus } from '../types';
import { STATUS_LABELS } from '../constants/boardFilters';

const STATUS_CLASS: Record<FlightStatus, string> = {
  SCHEDULED: 'status-badge--scheduled',
  DEPARTED: 'status-badge--departed',
  ARRIVED: 'status-badge--arrived',
  DELAYED: 'status-badge--delayed',
  CANCELLED: 'status-badge--cancelled',
};

/** Бейдж статуса рейса на табло. */
export function StatusBadge({ status }: { status: FlightStatus }) {
  return (
    <span className={`status-badge ${STATUS_CLASS[status] ?? ''}`}>
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}
