import type { ScheduleRs } from '../../../types';
import { isoDayOfWeekFromDate, isoDayOfWeekLabel, todayAirportDate } from '../../../utils/airportTime';
import { slotOptionLabel } from '../domain/flightFormatters';
import { Modal } from '../../../shared/components/Modal';

export interface CreateSlotOption {
  slotId: number;
  schedule: ScheduleRs;
  slot: { slotId: number; dayOfWeek?: number; departureTime: string; arrivalTime: string };
}

export interface FlightCreateModalProps {
  open: boolean;
  modalError: string | null;
  schedules: ScheduleRs[];
  availableSlots: CreateSlotOption[];
  modalScheduleFilter: string;
  onModalScheduleFilterChange: (value: string) => void;
  modalSlotId: string;
  onModalSlotIdChange: (value: string) => void;
  modalOperationDate: string;
  onModalOperationDateChange: (value: string) => void;
  selectedCreateSlot?: CreateSlotOption;
  createDateHint: string | null;
  createSaving: boolean;
  onClose: () => void;
  onSubmit: () => void;
}

/** Модалка создания рейса из слота шаблона. */
export function FlightCreateModal({
  open,
  modalError,
  schedules,
  availableSlots,
  modalScheduleFilter,
  onModalScheduleFilterChange,
  modalSlotId,
  onModalSlotIdChange,
  modalOperationDate,
  onModalOperationDateChange,
  selectedCreateSlot,
  createDateHint,
  createSaving,
  onClose,
  onSubmit,
}: FlightCreateModalProps) {
  const today = todayAirportDate();
  const minOperationDate = selectedCreateSlot?.schedule.effectiveFrom
    && selectedCreateSlot.schedule.effectiveFrom > today
    ? selectedCreateSlot.schedule.effectiveFrom
    : today;

  return (
    <Modal
      open={open}
      onClose={onClose}
      titleId="flight-create-title"
      closeOnBackdrop={false}
      closeOnEscape
      header={
        <>
          <h3 id="flight-create-title">Добавить новый рейс</h3>
          {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
          <p className="modal-hint modal-hint--compact">
            Выберите слот шаблона и дату операции — рейс будет создан на эту дату.
          </p>
        </>
      }
      footer={
        <div className="modal-actions modal-actions--center">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={createSaving}>
            Отмена
          </button>
          <button
            type="button"
            className="btn-primary"
            disabled={createSaving || availableSlots.length === 0}
            onClick={onSubmit}
          >
            {createSaving ? 'Создание…' : 'Создать'}
          </button>
        </div>
      }
    >
        <label>
          Шаблон (фильтр)
          <select
            value={modalScheduleFilter}
            onChange={e => {
              onModalScheduleFilterChange(e.target.value);
              onModalSlotIdChange('');
            }}
          >
            <option value="">— все шаблоны —</option>
            {schedules.filter(s => s.isActive !== false).map(s => (
              <option key={s.scheduleId} value={String(s.scheduleId)}>
                {s.flightNumber} {s.originAirport.trim()}→{s.destinationAirport.trim()}
              </option>
            ))}
          </select>
        </label>
        <label>
          Слот
          <select value={modalSlotId} onChange={e => onModalSlotIdChange(e.target.value)}>
            <option value="">— выберите слот —</option>
            {availableSlots.map(o => (
              <option key={o.slotId} value={String(o.slotId)}>
                {slotOptionLabel(o.schedule, o.slot)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Дата операции
          <input
            type="date"
            value={modalOperationDate}
            min={minOperationDate}
            max={selectedCreateSlot?.schedule.effectiveTo || undefined}
            onChange={e => onModalOperationDateChange(e.target.value)}
          />
        </label>
        {createDateHint && <p className="modal-hint">{createDateHint}</p>}
        {availableSlots.length === 0 && <p className="modal-alert">Нет доступных слотов.</p>}
    </Modal>
  );
}

/** Подсказка для даты создания рейса по выбранному слоту. */
export function buildCreateDateHint(
  selectedCreateSlot?: CreateSlotOption,
): string | null {
  if (!selectedCreateSlot) return null;
  const { schedule, slot } = selectedCreateSlot;
  const periodTo = schedule.effectiveTo ?? 'без ограничения';
  const dowPart = schedule.periodicityType === 'WEEKLY' && slot.dayOfWeek
    ? `Допустимые дни: ${isoDayOfWeekLabel(slot.dayOfWeek)}. `
    : schedule.periodicityType === 'INTERVAL'
      ? `INTERVAL: каждые ${schedule.periodicityStep} дн. от ${schedule.effectiveFrom}. `
      : '';
  return `${dowPart}Период шаблона: ${schedule.effectiveFrom} … ${periodTo}.`;
}

/** Валидация даты операции перед create (client-side UX). */
export function validateCreateFlightDate(
  modalOperationDate: string,
  selectedCreateSlot?: CreateSlotOption,
): string | null {
  if (!modalOperationDate) return 'Укажите дату операции.';
  const today = todayAirportDate();
  if (modalOperationDate < today) {
    return `Дата операции не может быть раньше ${today} (MSK).`;
  }
  if (selectedCreateSlot?.schedule.periodicityType === 'WEEKLY' && selectedCreateSlot.slot.dayOfWeek) {
    const dow = isoDayOfWeekFromDate(modalOperationDate);
    if (dow !== selectedCreateSlot.slot.dayOfWeek) {
      return `Дата не совпадает с днём недели слота (${isoDayOfWeekLabel(selectedCreateSlot.slot.dayOfWeek)}).`;
    }
  }
  return null;
}
