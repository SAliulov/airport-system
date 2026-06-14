import type { ScheduleRs } from '../../types';
import type { ScheduleRq } from '../../types/requests';
import { fromTimeInputValue, todayAirportDate, toTimeInputValue } from '../../utils/airportTime';
import type { ScheduleFormValues, ScheduleSlotFormValues } from '../../utils/fieldValidation';

export const DEFAULT_SCHEDULE_SLOT: ScheduleSlotFormValues = {
  dayOfWeek: '1',
  departureTime: '08:00',
  arrivalTime: '10:00',
};

export const EMPTY_SCHEDULE_FORM: ScheduleFormValues = {
  flightNumber: '',
  originAirport: '',
  destinationAirport: '',
  effectiveFrom: '',
  effectiveTo: '',
  isActive: true,
  periodicityType: 'WEEKLY',
  periodicityStep: '1',
  airlineId: '',
  slots: [{ ...DEFAULT_SCHEDULE_SLOT }],
};

/** Маппинг ScheduleRs → форма редактирования. */
export function scheduleToForm(schedule: ScheduleRs): ScheduleFormValues {
  const slots = schedule.slots?.length
    ? schedule.slots.map(s => ({
        slotId: s.slotId != null ? String(s.slotId) : undefined,
        dayOfWeek: s.dayOfWeek != null ? String(s.dayOfWeek) : '',
        departureTime: toTimeInputValue(s.departureTime),
        arrivalTime: toTimeInputValue(s.arrivalTime),
      }))
    : [{ ...DEFAULT_SCHEDULE_SLOT }];

  return {
    flightNumber: schedule.flightNumber,
    originAirport: schedule.originAirport.trim(),
    destinationAirport: schedule.destinationAirport.trim(),
    effectiveFrom: schedule.effectiveFrom ?? todayAirportDate(),
    effectiveTo: schedule.effectiveTo ?? '',
    isActive: schedule.isActive ?? true,
    periodicityType: schedule.periodicityType ?? 'WEEKLY',
    periodicityStep: String(schedule.periodicityStep ?? 1),
    airlineId: String(schedule.airline?.airlineId ?? ''),
    slots,
  };
}

/** Маппинг формы → тело запроса API. */
export function buildSchedulePayload(form: ScheduleFormValues): ScheduleRq {
  return {
    flightNumber: form.flightNumber.trim(),
    originAirport: form.originAirport.trim().toUpperCase(),
    destinationAirport: form.destinationAirport.trim().toUpperCase(),
    effectiveFrom: form.effectiveFrom,
    effectiveTo: form.effectiveTo || null,
    isActive: form.isActive,
    periodicityType: form.periodicityType,
    periodicityStep: Number(form.periodicityStep),
    airlineId: Number(form.airlineId),
    slots: form.slots.map(slot => ({
      ...(slot.slotId ? { slotId: Number(slot.slotId) } : {}),
      dayOfWeek: form.periodicityType === 'INTERVAL' ? null : Number(slot.dayOfWeek),
      departureTime: fromTimeInputValue(slot.departureTime) ?? slot.departureTime,
      arrivalTime: fromTimeInputValue(slot.arrivalTime) ?? slot.arrivalTime,
    })),
  };
}
