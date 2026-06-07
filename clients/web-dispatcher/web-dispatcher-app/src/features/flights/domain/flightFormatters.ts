import type {
  FlightRs,
  GateAssignmentRs,
  GateRs,
  GateSummaryRs,
  ScheduleRs,
  ScheduleSlotRs,
} from '../../../types';
import {
  formatAirportDateTime,
  formatAirportTime,
  isoDayOfWeekLabel,
} from '../../../utils/airportTime';

/** Текст ячейки «Тип ВС» в таблице рейсов. */
export function formatAircraftCell(flight: FlightRs): string {
  const ac = flight.aircraftType;
  if (!ac?.icaoCode) return 'Не назначен';
  const cat = ac.sizeCategory ? ` [${ac.sizeCategory}]` : '';
  return `${ac.icaoCode}${cat}`;
}

/** Краткая подпись гейта (номер, терминал, категория). */
export function formatGateLabel(g: GateSummaryRs): string {
  const term = g.terminal ? ` (${g.terminal})` : '';
  const cat = g.maxSizeCategory ? ` — [${g.maxSizeCategory}]` : '';
  return `${g.gateNumber}${term}${cat}`;
}

/** Подпись гейта для option в select. */
export function gateOptionLabel(gate: GateRs): string {
  const term = gate.terminal ? ` (${gate.terminal})` : '';
  const cat = gate.maxSizeCategory ? ` — [${gate.maxSizeCategory}]` : '';
  return `${gate.gateNumber}${term}${cat}`;
}

/** Интервал занятости гейта для tooltip / readonly блока. */
export function formatGateInterval(assignment?: GateAssignmentRs): string | null {
  if (!assignment?.assignedFrom && !assignment?.assignedTo) return null;
  return `${formatAirportDateTime(assignment.assignedFrom)} – ${formatAirportDateTime(assignment.assignedTo)}`;
}

/** Readonly подпись расписания рейса в модалке редактирования. */
export function flightScheduleLabel(flight: FlightRs): string {
  const s = flight.schedule;
  if (!s) return '—';
  const dayPart = flight.operationDate ? `${flight.operationDate} ` : '';
  return `${dayPart}${s.flightNumber} ${(s.originAirport ?? '').trim()} → ${(s.destinationAirport ?? '').trim()} (${formatAirportDateTime(flight.scheduledDeparture)})`;
}

/** Подпись слота в select создания рейса. */
export function slotOptionLabel(schedule: ScheduleRs, slot: ScheduleSlotRs): string {
  const dow = schedule.periodicityType === 'INTERVAL' ? '' : `${isoDayOfWeekLabel(slot.dayOfWeek)} `;
  return `${schedule.flightNumber} ${(schedule.originAirport ?? '').trim()}→${(schedule.destinationAirport ?? '').trim()} ${dow}${formatAirportTime(slot.departureTime)}–${formatAirportTime(slot.arrivalTime)}`;
}
