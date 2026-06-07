import {
  formatAirportTime,
  scheduleDepartureDate,
  todayAirportDate,
} from '../../../../shared/utils/airportTime';

export { getAirportTimezone } from '../../../../shared/utils/airportRuntime';

export {
  formatAirportTime,
  scheduleDepartureDate,
  todayAirportDate,
};

export function shiftAirportDate(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00`);
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** DD.MM из ISO date или scheduledDeparture */
export function formatCompactDate(value?: string | null): string {
  if (!value) return '—';
  const iso = value.length >= 10 && value.includes('-') ? value.slice(0, 10) : scheduleDepartureDate(value);
  if (!iso) return '—';
  const [, m, d] = iso.split('-');
  return `${d}.${m}`;
}
