import type { FlightRs, FlightStatus, GateAssignmentRs, ScheduleRs, SizeCategory } from '../../../types';
import { getHomeIata } from '../../../utils/airportTime';
import { toDatetimeLocalValue } from '../../../utils/airportTime';

/** Ранг категории размера ВС (совместимость с SizeCategory.isCompatible на backend). */
const SIZE_RANK: Record<SizeCategory, number> = { NARROW: 0, WIDE: 1, JUMBO: 2 };

export interface GateFormValues {
  gateId: string;
  assignedFrom: string;
  assignedTo: string;
}

/**
 * Проверяет совместимость типа ВС с максимальной категорией гейта.
 * Дублирует backend для UX (disabled options); источник истины — сервер.
 */
export function isAircraftFitsGate(aircraftSize?: SizeCategory, gateMax?: SizeCategory): boolean {
  if (!aircraftSize || !gateMax) return true;
  return SIZE_RANK[aircraftSize] <= SIZE_RANK[gateMax];
}

/** Режим «только фактические времена» для ARRIVED. */
export function isActualTimesOnlyMode(status: FlightStatus): boolean {
  return status === 'ARRIVED';
}

/** Ресурсы (ВС, гейт) закрыты для DEPARTED / ARRIVED / CANCELLED. */
export function isClosedFlightStatus(status: FlightStatus): boolean {
  return status === 'DEPARTED' || status === 'ARRIVED' || status === 'CANCELLED';
}

/**
 * Определяет направление рейса относительно домашнего аэропорта.
 */
export function flightDirection(schedule?: ScheduleRs): FlightDirection {
  const home = getHomeIata();
  const o = (schedule?.originAirport ?? '').trim().toUpperCase();
  const d = (schedule?.destinationAirport ?? '').trim().toUpperCase();
  if (o === home && d !== home) return 'outbound';
  if (d === home && o !== home) return 'inbound';
  return null;
}

/**
 * Можно ли назначать/менять гейт в текущем статусе.
 * Дублирует FlightMutationBusinessRules для UX; источник истины — backend.
 */
export function isGateMutable(status: FlightStatus, schedule?: ScheduleRs): boolean {
  if (status === 'SCHEDULED' || status === 'DELAYED') return true;
  if (status === 'DEPARTED' && flightDirection(schedule) === 'inbound') return true;
  return false;
}

/** Интервал гейта в БД повреждён (assigned_to < assigned_from). */
export function isGateIntervalDamaged(assignment?: GateAssignmentRs): boolean {
  if (!assignment?.assignedFrom || !assignment?.assignedTo) return false;
  return assignment.assignedTo < assignment.assignedFrom;
}

/**
 * Допустимые ручные переходы статуса для select в модалке.
 * Дублирует FlightStatusBusinessRules для UX; источник истины — backend.
 */
export function allowedStatusOptions(detail: FlightRs): FlightStatus[] {
  const dir = flightDirection(detail.schedule);
  const s = detail.status;
  if (dir === 'outbound') {
    const opts: FlightStatus[] = [];
    if (s === 'SCHEDULED') opts.push('DELAYED', 'DEPARTED', 'CANCELLED');
    if (s === 'DELAYED') opts.push('DEPARTED', 'CANCELLED');
    if (s === 'DEPARTED') opts.push('ARRIVED');
    return opts;
  }
  if (dir === 'inbound') {
    const opts: FlightStatus[] = [];
    if (s === 'SCHEDULED') opts.push('DELAYED', 'DEPARTED', 'CANCELLED');
    if (s === 'DELAYED') opts.push('DEPARTED', 'CANCELLED', 'ARRIVED');
    if (s === 'DEPARTED') opts.push('ARRIVED');
    return opts;
  }
  if (s === 'SCHEDULED' || s === 'DELAYED') return ['CANCELLED'];
  return [];
}

/** Форма гейта из существующего назначения. */
export function gateFormFromAssignment(assignment?: GateAssignmentRs): GateFormValues {
  if (!assignment?.gate?.gateId) {
    return { gateId: '', assignedFrom: '', assignedTo: '' };
  }
  return {
    gateId: String(assignment.gate.gateId),
    assignedFrom: toDatetimeLocalValue(assignment.assignedFrom),
    assignedTo: toDatetimeLocalValue(assignment.assignedTo),
  };
}

/** Начальные значения формы гейта из рейса (плановые времена по умолчанию). */
export function gateFormFromFlight(detail: FlightRs): GateFormValues {
  const dep = toDatetimeLocalValue(detail.scheduledDeparture);
  const arr = toDatetimeLocalValue(detail.scheduledArrival);
  const assignment = detail.currentGateAssignment;
  if (!assignment?.gate?.gateId) {
    return { gateId: '', assignedFrom: dep, assignedTo: arr };
  }
  const fromAssignment = gateFormFromAssignment(assignment);
  return {
    gateId: fromAssignment.gateId,
    assignedFrom: fromAssignment.assignedFrom || dep,
    assignedTo: fromAssignment.assignedTo || arr,
  };
}

/** Изменилась ли форма гейта относительно текущего назначения. */
export function gateAssignmentChanged(form: GateFormValues, current?: GateAssignmentRs): boolean {
  if (!form.gateId) return false;
  if (!current?.gate?.gateId) return true;
  const baseline = gateFormFromAssignment(current);
  return (
    form.gateId !== baseline.gateId
    || form.assignedFrom !== baseline.assignedFrom
    || form.assignedTo !== baseline.assignedTo
  );
}
