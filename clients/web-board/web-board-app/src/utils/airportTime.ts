export const AIRPORT_TZ = 'Europe/Moscow';

const NAIVE_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/;

export function todayAirportDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: AIRPORT_TZ }).format(new Date());
}

export function scheduleDepartureDate(scheduledDeparture?: string | null): string | null {
  if (!scheduledDeparture) return null;
  const match = scheduledDeparture.trim().match(NAIVE_DATE_TIME);
  if (match) return `${match[1]}-${match[2]}-${match[3]}`;
  const normalized = scheduledDeparture.includes('T')
    ? scheduledDeparture
    : scheduledDeparture.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return null;
  return new Intl.DateTimeFormat('en-CA', { timeZone: AIRPORT_TZ }).format(d);
}

function addDaysIso(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00`);
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function isWithinDaysFromToday(isoDate: string, days: number, today = todayAirportDate()): boolean {
  const end = addDaysIso(today, days);
  return isoDate >= today && isoDate <= end;
}

export function formatAirportTime(value?: string | null): string {
  if (!value) return '—';
  const trimmed = value.trim();
  const timeOnly = trimmed.match(/^(\d{2}):(\d{2})/);
  if (timeOnly && !trimmed.includes('T') && !/^\d{4}-/.test(trimmed)) {
    return `${timeOnly[1]}:${timeOnly[2]}`;
  }
  const match = trimmed.match(NAIVE_DATE_TIME);
  if (match) return `${match[4]}:${match[5]}`;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleTimeString('ru-RU', {
    timeZone: AIRPORT_TZ,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}
