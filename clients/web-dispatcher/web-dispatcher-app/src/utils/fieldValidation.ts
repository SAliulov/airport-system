import {
  formatShortDateWithDow,
  fromTimeInputValue,
  getHomeIata,
  isoDayOfWeekLabel,
  slotDayFitsEffectivePeriod,
} from './airportTime';
import type { PeriodicityType } from '../types';

export type ValidationResult = {
  ok: boolean;
  errors: Record<string, string>;
};

const IATA_AIRPORT = /^[A-Za-z]{3}$/;
const IATA_AIRLINE = /^[A-Za-z0-9]{2}$/;
const ICAO_CODE = /^[A-Za-z0-9]{2,4}$/;

const SLOT_PERIOD_RE =
  /Слот для дня недели (\d+) не попадает в короткий период действия расписания \(с (\d{4}-\d{2}-\d{2}) по (\d{4}-\d{2}-\d{2})\)/;

export function formatSlotPeriodHumorMessage(
  effectiveFrom: string,
  effectiveTo: string,
  slotDayOfWeek: number,
): string {
  const to = effectiveTo || effectiveFrom;
  const fromLabel = formatShortDateWithDow(effectiveFrom);
  const toLabel = formatShortDateWithDow(to);
  const slotLabel = isoDayOfWeekLabel(slotDayOfWeek);
  return `Дружище, ты выбрал период с ${fromLabel} по ${toLabel}, какое к чёрту ${slotLabel}?`;
}

export interface ScheduleSlotFormValues {
  slotId?: string;
  dayOfWeek: string;
  departureTime: string;
  arrivalTime: string;
}

export interface ScheduleFormValues {
  flightNumber: string;
  originAirport: string;
  destinationAirport: string;
  effectiveFrom: string;
  effectiveTo: string;
  isActive: boolean;
  periodicityType: PeriodicityType;
  periodicityStep: string;
  airlineId: string;
  slots: ScheduleSlotFormValues[];
}

export interface AirlineFormValues {
  iataCode: string;
  name: string;
  country: string;
}

export interface AircraftTypeFormValues {
  icaoCode: string;
  passengerCapacity: string;
  sizeCategory: string;
}

export interface GateFormValues {
  gateNumber: string;
  terminal: string;
  maxSizeCategory: string;
}

function fail(errors: Record<string, string>): ValidationResult {
  return { ok: false, errors };
}

function ok(): ValidationResult {
  return { ok: true, errors: {} };
}

export function mapScheduleBackendError(
  error: unknown,
  _form: ScheduleFormValues,
): { fieldErrors: Record<string, string>; message: string } | null {
  const text = error instanceof Error ? error.message : String(error);
  const match = text.match(SLOT_PERIOD_RE);

  if (match) {
    const dow = Number(match[1]);
    const from = match[2];
    const to = match[3];
    return {
      fieldErrors: { slots: formatSlotPeriodHumorMessage(from, to, dow) },
      message: 'Исправьте ошибки в форме.',
    };
  }

  return null;
}

export function validateScheduleForm(form: ScheduleFormValues): ValidationResult {
  const errors: Record<string, string> = {};
  const flightNumber = form.flightNumber.trim();

  if (!flightNumber) {
    errors.flightNumber = 'Укажите номер рейса.';
  } else if (flightNumber.length > 20) {
    errors.flightNumber = 'Номер рейса: не более 20 символов.';
  }

  const origin = form.originAirport.trim().toUpperCase();
  if (!origin) {
    errors.originAirport = 'Укажите аэропорт вылета (IATA).';
  } else if (!IATA_AIRPORT.test(origin)) {
    errors.originAirport = 'IATA аэропорта: 3 латинские буквы.';
  }

  const destination = form.destinationAirport.trim().toUpperCase();
  if (!destination) {
    errors.destinationAirport = 'Укажите аэропорт прилёта (IATA).';
  } else if (!IATA_AIRPORT.test(destination)) {
    errors.destinationAirport = 'IATA аэропорта: 3 латинские буквы.';
  } else if (origin && origin === destination) {
    errors.destinationAirport = 'Аэропорты вылета и прилёта должны различаться.';
  } else if (origin && destination && origin !== getHomeIata() && destination !== getHomeIata()) {
    errors.destinationAirport = `Маршрут должен проходить через базовый аэропорт (${getHomeIata()}).`;
  }

  if (!form.effectiveFrom) {
    errors.effectiveFrom = 'Укажите дату начала действия шаблона.';
  }
  if (form.effectiveTo && form.effectiveFrom && form.effectiveTo < form.effectiveFrom) {
    errors.effectiveTo = 'Дата окончания не может быть раньше даты начала.';
  }

  const step = Number(form.periodicityStep);
  if (!form.periodicityStep || !Number.isInteger(step) || step < 1) {
    errors.periodicityStep = 'Шаг периодичности: целое число ≥ 1.';
  }

  if (!form.airlineId) {
    errors.airlineId = 'Выберите авиакомпанию.';
  }

  if (!form.slots.length) {
    errors.slots = 'Укажите хотя бы один слот.';
  } else if (form.periodicityType === 'INTERVAL') {
    if (form.slots.length !== 1) {
      errors.slots = 'Для INTERVAL допускается ровно один слот.';
    } else if (form.slots[0].dayOfWeek) {
      errors.slots = 'Для INTERVAL день недели слота должен быть пустым.';
    }
  } else {
    const seen = new Set<number>();
    for (const slot of form.slots) {
      const dow = Number(slot.dayOfWeek);
      if (!slot.dayOfWeek || !Number.isInteger(dow) || dow < 1 || dow > 7) {
        errors.slots = 'Для WEEKLY укажите день недели слота от 1 (Пн) до 7 (Вс).';
        break;
      }
      if (seen.has(dow)) {
        errors.slots = `День недели ${dow} указан более одного раза.`;
        break;
      }
      seen.add(dow);
    }

    if (form.effectiveFrom) {
      const periodTo = form.effectiveTo || form.effectiveFrom;
      for (const slot of form.slots) {
        const dow = Number(slot.dayOfWeek);
        if (Number.isInteger(dow) && !slotDayFitsEffectivePeriod(dow, form.effectiveFrom, periodTo)) {
          errors.slots = formatSlotPeriodHumorMessage(form.effectiveFrom, periodTo, dow);
          break;
        }
      }
    }
  }

  for (const slot of form.slots) {
    if (!slot.departureTime) {
      errors.slots = 'Укажите время вылета для каждого слота.';
      break;
    }
    if (!slot.arrivalTime) {
      errors.slots = 'Укажите время прилёта для каждого слота.';
      break;
    }
    const dep = fromTimeInputValue(slot.departureTime);
    const arr = fromTimeInputValue(slot.arrivalTime);
    if (dep && arr && dep === arr) {
      errors.slots = 'Время вылета и прилёта слота не могут совпадать.';
      break;
    }
  }

  return Object.keys(errors).length > 0 ? fail(errors) : ok();
}

export function validateAirlineForm(form: AirlineFormValues): ValidationResult {
  const errors: Record<string, string> = {};
  const iataCode = form.iataCode.trim().toUpperCase();

  if (!iataCode) {
    errors.iataCode = 'Укажите IATA-код авиакомпании.';
  } else if (!IATA_AIRLINE.test(iataCode)) {
    errors.iataCode = 'IATA-код авиакомпании: ровно 2 латинские буквы или цифры.';
  }

  const name = form.name.trim();
  if (!name) {
    errors.name = 'Укажите название авиакомпании.';
  } else if (name.length > 100) {
    errors.name = 'Название: не более 100 символов.';
  }

  const country = form.country.trim();
  if (country.length > 77) {
    errors.country = 'Страна: не более 77 символов.';
  }

  return Object.keys(errors).length > 0 ? fail(errors) : ok();
}

export function validateAircraftTypeForm(form: AircraftTypeFormValues): ValidationResult {
  const errors: Record<string, string> = {};
  const icaoCode = form.icaoCode.trim().toUpperCase();

  if (!icaoCode) {
    errors.icaoCode = 'Укажите ICAO-код типа ВС.';
  } else if (!ICAO_CODE.test(icaoCode)) {
    errors.icaoCode = 'ICAO-код типа ВС: 2–4 латинских буквы или цифры.';
  }

  if (form.passengerCapacity !== '') {
    const n = Number(form.passengerCapacity);
    if (!Number.isInteger(n) || n < 0) {
      errors.passengerCapacity = 'Вместимость: целое число ≥ 0.';
    }
  }

  if (!form.sizeCategory) {
    errors.sizeCategory = 'Выберите категорию размера.';
  }

  return Object.keys(errors).length > 0 ? fail(errors) : ok();
}

export function validateGateForm(form: GateFormValues): ValidationResult {
  const errors: Record<string, string> = {};
  const gateNumber = form.gateNumber.trim();

  if (!gateNumber) {
    errors.gateNumber = 'Укажите номер гейта.';
  } else if (gateNumber.length > 10) {
    errors.gateNumber = 'Номер гейта: не более 10 символов.';
  }

  const terminal = form.terminal.trim();
  if (terminal.length > 10) {
    errors.terminal = 'Терминал: не более 10 символов.';
  }

  if (!form.maxSizeCategory) {
    errors.maxSizeCategory = 'Выберите максимальный размер ВС.';
  }

  return Object.keys(errors).length > 0 ? fail(errors) : ok();
}
