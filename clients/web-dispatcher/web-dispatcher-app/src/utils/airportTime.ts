export {
  formatAirportDateTime,
  formatAirportTime,
  naiveWallMs,
  parseNaiveWall,
  scheduleDepartureDate,
  todayAirportDate,
  todayAirportDateInTz,
} from '../../../../shared/utils/airportTime';

export {
  getAirportRuntime,
  getAirportTimezone,
  getGatePlanWindowHours,
  getGatePostGraceMinutes,
  getHomeIata,
} from '../../../../shared/utils/airportRuntime';

const NAIVE_DATE_TIME = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/;
const DATETIME_LOCAL = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

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

/** ISO 1=Пн … 7=Вс для календарной даты YYYY-MM-DD (UTC-noon, без сдвига TZ браузера). */
export function isoDayOfWeekFromDate(date: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  const [y, mo, d] = date.split('-').map(Number);
  const utc = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  const js = utc.getUTCDay();
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
  const [y, mo, d] = isoDate.split('-').map(Number);
  const utc = new Date(Date.UTC(y, mo - 1, d, 12, 0, 0));
  utc.setUTCDate(utc.getUTCDate() + days);
  return `${utc.getUTCFullYear()}-${String(utc.getUTCMonth() + 1).padStart(2, '0')}-${String(utc.getUTCDate()).padStart(2, '0')}`;
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
