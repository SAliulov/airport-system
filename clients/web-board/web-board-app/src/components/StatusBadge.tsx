import type { FlightStatus } from '../types';

const LABELS: Record<FlightStatus, string> = {
  SCHEDULED: 'По расписанию',
  DEPARTED: 'Вылетел',
  ARRIVED: 'Прибыл',
  DELAYED: 'Задержан',
  CANCELLED: 'Отменён',
};

const COLORS: Record<FlightStatus, string> = {
  SCHEDULED: '#3b82f6',
  DEPARTED: '#f97316',
  ARRIVED: '#22c55e',
  DELAYED: '#ef4444',
  CANCELLED: '#6b7280',
};

export function StatusBadge({ status }: { status: FlightStatus }) {
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 10px',
        borderRadius: 4,
        fontSize: 13,
        fontWeight: 600,
        letterSpacing: '0.03em',
        color: '#fff',
        background: COLORS[status] ?? '#555',
      }}
    >
      {LABELS[status] ?? status}
    </span>
  );
}
