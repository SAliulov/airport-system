import type { FlightStatus } from '../types';

export type DayPreset = 'yesterday' | 'today' | 'tomorrow';

export const BOARD_PAGE_SIZE = 20;

export const DAY_PRESETS: { id: DayPreset; label: string }[] = [
  { id: 'yesterday', label: 'Вчера' },
  { id: 'today', label: 'Сегодня' },
  { id: 'tomorrow', label: 'Завтра' },
];

export const TIME_SLOTS: { hourFrom: number | null; label: string }[] = [
  { hourFrom: null, label: 'Любое время' },
  ...Array.from({ length: 12 }, (_, i) => {
    const h = i * 2;
    const pad = (n: number) => String(n).padStart(2, '0');
    return {
      hourFrom: h,
      label: `${pad(h)}:00–${pad((h + 2) % 24)}:00`,
    };
  }),
];

export const FILTER_STATUSES: FlightStatus[] = [
  'SCHEDULED',
  'DEPARTED',
  'ARRIVED',
  'DELAYED',
  'CANCELLED',
];

export const STATUS_LABELS: Record<FlightStatus, string> = {
  SCHEDULED: 'По расписанию',
  DEPARTED: 'Вылетел',
  ARRIVED: 'Прибыл',
  DELAYED: 'Задержан',
  CANCELLED: 'Отменён',
};

export const BOARD_WS_TOPICS = ['/topic/flights', '/topic/delays', '/topic/gate-changes'] as const;

export interface BoardFilterState {
  dayPreset: DayPreset;
  hourFrom: number | null;
  terminal: string;
  search: string;
  status: FlightStatus | '';
  origin: string;
  destination: string;
  airlineId: string;
}

export const EMPTY_BOARD_FILTERS: BoardFilterState = {
  dayPreset: 'today',
  hourFrom: null,
  terminal: '',
  search: '',
  status: '',
  origin: '',
  destination: '',
  airlineId: '',
};
