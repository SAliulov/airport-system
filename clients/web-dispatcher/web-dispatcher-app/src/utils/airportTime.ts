/** Wall-clock times for the home airport (must match backend airport.timezone). */

export const AIRPORT_TZ = 'Europe/Moscow';

const NAIVE_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/;
const DATETIME_LOCAL = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

/** Сегодня в календаре аэропорта (YYYY-MM-DD для input[type=date]). */
export function todayAirportDate(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: AIRPORT_TZ }).format(new Date());
}

/** Календарный день планового вылета из API naive datetime. */
export function scheduleDepartureDate(scheduledDeparture?: string | null): string | null {
  if (!scheduledDeparture) return null;
  const wall = parseNaiveWall(scheduledDeparture);
  if (wall) return `${wall.y}-${wall.mo}-${wall.d}`;
  const normalized = scheduledDeparture.includes('T')
    ? scheduledDeparture
    : scheduledDeparture.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return null;
  return new Intl.DateTimeFormat('en-CA', { timeZone: AIRPORT_TZ }).format(d);
}

/** Parse API naive local datetime without browser timezone shift. */
function parseNaiveWall(value: string): { y: string; mo: string; d: string; h: string; mi: string } | null {
  const match = value.trim().match(NAIVE_DATE_TIME);
  if (!match) return null;
  return { y: match[1], mo: match[2], d: match[3], h: match[4], mi: match[5] };
}

/** Display in tables: 2026-05-19T14:30:00 → 19.05.2026 14:30 */
export function formatAirportDateTime(value?: string | null): string {
  if (!value) return '—';
  const wall = parseNaiveWall(value);
  if (wall) return `${wall.d}.${wall.mo}.${wall.y} ${wall.h}:${wall.mi}`;
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const d = new Date(normalized);
  if (Number.isNaN(d.getTime())) return value.slice(0, 16).replace('T', ' ');
  return d.toLocaleString('ru-RU', {
    timeZone: AIRPORT_TZ,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).replace(',', '');
}

/** API ISO/local string → value for input[type=datetime-local] (airport wall time, no TZ shift). */
export function toDatetimeLocalValue(apiValue?: string | null): string {
  if (!apiValue) return '';
  const match = apiValue.trim().match(NAIVE_DATE_TIME);
  if (match) return `${match[1]}T${match[4]}:${match[5]}`;
  return '';
}

export function isValidDatetimeLocal(value: string): boolean {
  return DATETIME_LOCAL.test(value.trim());
}

/** datetime-local value → API body (naive local airport time). */
export function fromDatetimeLocalValue(value: string): string | undefined {
  const trimmed = value.trim();
  if (!trimmed || !isValidDatetimeLocal(trimmed)) return undefined;
  return `${trimmed}:00`;
}

/** API LocalTime → value for input[type=time]. */
export function toTimeInputValue(apiValue?: string | null): string {
  if (!apiValue) return '';
  const match = apiValue.trim().match(/^(\d{2}):(\d{2})/);
  return match ? `${match[1]}:${match[2]}` : '';
}

/** input[type=time] → API LocalTime (HH:mm:ss). */
export function fromTimeInputValue(value: string): string | undefined {
  const trimmed = value.trim();
  if (!/^\d{2}:\d{2}$/.test(trimmed)) return undefined;
  return `${trimmed}:00`;
}

const ISO_DOW_LABELS: Record<number, string> = {
  1: 'Пн', 2: 'Вт', 3: 'Ср', 4: 'Чт', 5: 'Пт', 6: 'Сб', 7: 'Вс',
};

export function isoDayOfWeekLabel(dow?: number | null): string {
  if (dow == null) return '';
  return ISO_DOW_LABELS[dow] ?? String(dow);
}

/** ISO 1=Пн … 7=Вс для календарной даты YYYY-MM-DD. */
export function isoDayOfWeekFromDate(date: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  const d = new Date(`${date}T12:00:00`);
  if (Number.isNaN(d.getTime())) return null;
  const js = d.getDay();
  return js === 0 ? 7 : js;
}

/** «Пн 01.06.2026» для ISO-даты. */
export function formatShortDateWithDow(isoDate: string): string {
  const match = isoDate.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!match) return isoDate;
  const dow = isoDayOfWeekFromDate(isoDate);
  const label = dow != null ? isoDayOfWeekLabel(dow) : '';
  return `${label} ${match[3]}.${match[2]}.${match[1]}`.trim();
}

function addDaysIso(isoDate: string, days: number): string {
  const d = new Date(`${isoDate}T12:00:00`);
  d.setDate(d.getDate() + days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Есть ли в [effectiveFrom…effectiveTo] хотя бы один день с заданным ISO DOW. */
export function slotDayFitsEffectivePeriod(
  dow: number,
  effectiveFrom: string,
  effectiveTo: string,
): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(effectiveFrom)) return true;
  const to = /^\d{4}-\d{2}-\d{2}$/.test(effectiveTo) ? effectiveTo : effectiveFrom;
  let current = effectiveFrom;
  while (current <= to) {
    if (isoDayOfWeekFromDate(current) === dow) return true;
    current = addDaysIso(current, 1);
  }
  return false;
}

/** Time only for tablo and schedule slots (API LocalTime or naive datetime). */
export function formatAirportTime(value?: string | null): string {
  if (!value) return '—';
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
    timeZone: AIRPORT_TZ,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
}
