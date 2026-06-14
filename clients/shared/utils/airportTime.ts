import { getAirportTimezone } from './airportRuntime';

const NAIVE_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/;

/** Parse API naive local datetime without browser timezone shift. */
function parseNaiveWall(value: string): { y: string; mo: string; d: string; h: string; mi: string } | null {
  const match = value.trim().match(NAIVE_DATE_TIME);
  if (!match) return null;
  return { y: match[1], mo: match[2], d: match[3], h: match[4], mi: match[5] };
}

function todayAirportDateInTz(timezone: string, at: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone }).format(at);
}

/** Сегодня в календаре аэропорта (требует загруженный airport runtime). */
export function todayAirportDate(at: Date = new Date()): string {
  return todayAirportDateInTz(getAirportTimezone(), at);
}

/** Календарный день планового вылета из API naive datetime. */
export function scheduleDepartureDate(scheduledDeparture?: string | null, timezone?: string): string | null {
  if (!scheduledDeparture) return null;
  const tz = timezone ?? getAirportTimezone();
  const wall = parseNaiveWall(scheduledDeparture);
  if (wall) return `${wall.y}-${wall.mo}-${wall.d}`;
  const normalized = scheduledDeparture.includes('T')
    ? scheduledDeparture
    : scheduledDeparture.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return null;
  return new Intl.DateTimeFormat('en-CA', { timeZone: tz }).format(d);
}

/** Time only for tablo and schedule slots (API LocalTime or naive datetime). */
export function formatAirportTime(value?: string | null, timezone?: string): string {
  if (!value) return '—';
  const tz = timezone ?? getAirportTimezone();
  const trimmed = value.trim();
  const timeOnly = trimmed.match(/^(\d{2}):(\d{2})/);
  if (timeOnly && !trimmed.includes('T') && !/^\d{4}-/.test(trimmed)) {
    return `${timeOnly[1]}:${timeOnly[2]}`;
  }
  const wall = parseNaiveWall(value);
  if (wall) return `${wall.h}:${wall.mi}`;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleTimeString('ru-RU', {
    timeZone: tz,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}

/** Display in tables: 2026-05-19T14:30:00 → 19.05.2026 14:30 */
export function formatAirportDateTime(value?: string | null, timezone?: string): string {
  if (!value) return '—';
  const tz = timezone ?? getAirportTimezone();
  const wall = parseNaiveWall(value);
  if (wall) return `${wall.d}.${wall.mo}.${wall.y} ${wall.h}:${wall.mi}`;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return value.slice(0, 16).replace('T', ' ');
  return d.toLocaleString('ru-RU', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).replace(',', '');
}

/** Naive wall datetime → epoch ms without browser TZ shift. */
export function naiveWallMs(value: string): number {
  const match = value.trim().match(NAIVE_DATE_TIME);
  if (!match) return 0;
  const y = Number(match[1]);
  const mo = Number(match[2]) - 1;
  const d = Number(match[3]);
  const h = Number(match[4]);
  const mi = Number(match[5]);
  return Date.UTC(y, mo, d, h, mi, 0, 0);
}
