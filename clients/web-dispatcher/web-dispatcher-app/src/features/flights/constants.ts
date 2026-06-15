import type { FlightStatus } from '../../types';
import { getAirportTimezone, getHomeIata } from '../../utils/airportTime';

export const FILTER_STATUSES: FlightStatus[] = [
  'SCHEDULED',
  'DEPARTED',
  'ARRIVED',
  'DELAYED',
  'CANCELLED',
];

export const FLIGHT_WS_TOPICS = ['/topic/flights', '/topic/delays', '/topic/gate-changes', '/topic/operational-events'] as const;

export function getFlightsRulesTooltip(): string {
  const home = getHomeIata();
  const tz = getAirportTimezone();
  return 'Изменять расписание и тип ВС можно только для SCHEDULED и DELAYED. '
    + `Гейт ${home}: SCHEDULED/DELAYED; для inbound в статусе DEPARTED — назначение и смена гейта разрешены до прилёта. `
    + 'Outbound DEPARTED — гейт закрыт; интервал «По» не перезаписывается фактическим вылетом. '
    + `Переход в DEPARTED требует фактическое время вылета, тип ВС и гейт ${home} (outbound). `
    + `Переход в ARRIVED — фактическое время прилёта и гейт ${home} (inbound). `
    + `Фактические времена — ${tz}, не часовой пояс браузера. `
    + 'Сортировка списка: клик по «План вылет» или выбор в фильтре ↑/↓.';
}

export const FLIGHTS_PAGE_SIZE = 20;
