import { useMemo, useState } from 'react';
import {
  buildCreateDateHint,
  validateCreateFlightDate,
} from '../components/FlightCreateModal';
import type { Dispatch, SetStateAction } from 'react';
import { createFlight, generateFlights, getSchedules } from '../../../services/api';
import { deleteFlightsBySchedule } from '../../../services/api/flights';
import { formatApiError } from '../../../utils/apiError';
import { formatAirportDateTime, todayAirportDate } from '../../../utils/airportTime';
import type { ScheduleRs, ScheduleSlotRs } from '../../../types';
import type { FlightGenerateRq } from '../../../types/requests';

export interface CreateSuccessBanner {
  flightId: number;
  label: string;
  showDate: string;
}

export interface UseFlightPageModalsOptions {
  schedules: ScheduleRs[];
  setSchedules: Dispatch<SetStateAction<ScheduleRs[]>>;
  filterDate: string;
  setFilterDate: (date: string) => void;
  load: (overrideDate?: string) => void;
}

/**
 * Состояние и обработчики модалок «Создать рейс» и «Генерация» на FlightsPage.
 */
export function useFlightPageModals({
  schedules,
  setSchedules,
  filterDate,
  setFilterDate,
  load,
}: UseFlightPageModalsOptions) {
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [modalScheduleFilter, setModalScheduleFilter] = useState('');
  const [modalSlotId, setModalSlotId] = useState('');
  const [modalOperationDate, setModalOperationDate] = useState('');
  const [createSaving, setCreateSaving] = useState(false);
  const [createSuccess, setCreateSuccess] = useState<CreateSuccessBanner | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);

  const [generateModalOpen, setGenerateModalOpen] = useState(false);
  const [generateFrom, setGenerateFrom] = useState('');
  const [generateTo, setGenerateTo] = useState('');
  const [generateScheduleId, setGenerateScheduleId] = useState('');
  const [generateSaving, setGenerateSaving] = useState(false);
  const [bulkDeleteSaving, setBulkDeleteSaving] = useState(false);
  const [generateInfo, setGenerateInfo] = useState<string | null>(null);
  const [bulkDeleteConfirmLabel, setBulkDeleteConfirmLabel] = useState<string | null>(null);

  const availableSlots = useMemo(() => {
    const opts: { slotId: number; schedule: ScheduleRs; slot: ScheduleSlotRs }[] = [];
    for (const schedule of schedules) {
      if (schedule.isActive === false) continue;
      if (modalScheduleFilter && String(schedule.scheduleId) !== modalScheduleFilter) continue;
      for (const slot of schedule.slots ?? []) {
        opts.push({ slotId: slot.slotId, schedule, slot });
      }
    }
    return opts;
  }, [schedules, modalScheduleFilter]);

  const selectedCreateSlot = useMemo(
    () => availableSlots.find(o => String(o.slotId) === modalSlotId),
    [availableSlots, modalSlotId],
  );

  const createDateHint = useMemo(
    () => buildCreateDateHint(selectedCreateSlot),
    [selectedCreateSlot],
  );

  async function openCreateModal() {
    setModalError(null);
    setModalScheduleFilter('');
    setModalSlotId('');
    setModalOperationDate(filterDate || todayAirportDate());
    try {
      setSchedules(await getSchedules());
    } catch (e: unknown) {
      setModalError(formatApiError(e));
      return;
    }
    setCreateModalOpen(true);
  }

  function closeCreateModal() {
    setCreateModalOpen(false);
    setModalScheduleFilter('');
    setModalSlotId('');
    setModalOperationDate('');
    setModalError(null);
  }

  function openGenerateModal() {
    setGenerateInfo(null);
    setGenerateFrom(filterDate || todayAirportDate());
    setGenerateTo(filterDate || todayAirportDate());
    setGenerateScheduleId('');
    setGenerateModalOpen(true);
  }

  async function submitGenerate() {
    if (!generateFrom || !generateTo) {
      setGenerateInfo('Укажите период from/to.');
      return;
    }
    if (generateTo < generateFrom) {
      setGenerateInfo('Дата окончания не может быть раньше даты начала.');
      return;
    }
    const today = todayAirportDate();
    if (generateFrom < today) {
      setGenerateInfo(`Период генерации не может начинаться раньше ${today} (MSK).`);
      return;
    }
    setGenerateSaving(true);
    setGenerateInfo(null);
    try {
      const body: FlightGenerateRq = {
        fromDate: generateFrom,
        toDate: generateTo,
        ...(generateScheduleId ? { scheduleId: Number(generateScheduleId) } : {}),
      };
      const result = await generateFlights(body);
      if (result.created === 0 && result.skipped === 0) {
        setGenerateInfo(
          'За указанный период не создано ни одного рейса: нет подходящих слотов или все уже существуют.',
        );
      } else {
        setGenerateInfo(
          `Создано: ${result.created}, пропущено: ${result.skipped}. Фильтр по дате сброшен — в списке видны все дни периода.`,
        );
        setFilterDate('');
        load('');
      }
    } catch (e: unknown) {
      setGenerateInfo(formatApiError(e));
    } finally {
      setGenerateSaving(false);
    }
  }

  function requestBulkDeleteBySchedule() {
    if (!generateScheduleId) {
      setGenerateInfo('Выберите шаблон, по которому нужно удалить сгенерированные рейсы.');
      return;
    }
    const schedule = schedules.find(s => String(s.scheduleId) === generateScheduleId);
    const label = schedule
      ? `${schedule.flightNumber} ${schedule.originAirport.trim()}→${schedule.destinationAirport.trim()}`
      : `#${generateScheduleId}`;
    setBulkDeleteConfirmLabel(label);
  }

  function cancelBulkDeleteConfirm() {
    setBulkDeleteConfirmLabel(null);
  }

  async function confirmBulkDeleteBySchedule() {
    if (!generateScheduleId) return;
    setBulkDeleteSaving(true);
    setGenerateInfo(null);
    try {
      const result = await deleteFlightsBySchedule(Number(generateScheduleId));
      setGenerateInfo(`Удалено рейсов по шаблону: ${result.deleted}.`);
      setFilterDate('');
      setSchedules(await getSchedules());
      load('');
      setBulkDeleteConfirmLabel(null);
    } catch (e: unknown) {
      setGenerateInfo(formatApiError(e));
    } finally {
      setBulkDeleteSaving(false);
    }
  }

  async function submitCreateFlight() {
    const slotId = Number(modalSlotId);
    if (!modalSlotId || !Number.isInteger(slotId) || slotId <= 0) {
      setModalError('Выберите слот из списка.');
      return;
    }
    const dateError = validateCreateFlightDate(modalOperationDate, selectedCreateSlot);
    if (dateError) {
      setModalError(dateError);
      return;
    }
    const picked = availableSlots.find(o => o.slotId === slotId);
    try {
      setModalError(null);
      setCreateSaving(true);
      const created = await createFlight({ slotId, operationDate: modalOperationDate });
      closeCreateModal();
      load();
      const depDay = created.operationDate ?? modalOperationDate;
      const label = picked
        ? `${picked.schedule.flightNumber}, ${formatAirportDateTime(created.scheduledDeparture)}`
        : `#${created.flightId}`;
      if (depDay) {
        setCreateSuccess({ flightId: created.flightId, label, showDate: depDay });
      }
    } catch (e: unknown) {
      setModalError(formatApiError(e));
    } finally {
      setCreateSaving(false);
    }
  }

  return {
    createModalOpen,
    modalScheduleFilter,
    setModalScheduleFilter,
    modalSlotId,
    setModalSlotId,
    modalOperationDate,
    setModalOperationDate,
    createSaving,
    createSuccess,
    setCreateSuccess,
    modalError,
    availableSlots,
    selectedCreateSlot,
    createDateHint,
    openCreateModal,
    closeCreateModal,
    submitCreateFlight,
    generateModalOpen,
    setGenerateModalOpen,
    generateFrom,
    setGenerateFrom,
    generateTo,
    setGenerateTo,
    generateScheduleId,
    setGenerateScheduleId,
    generateSaving,
    bulkDeleteSaving,
    generateInfo,
    openGenerateModal,
    submitGenerate,
    bulkDeleteConfirmLabel,
    requestBulkDeleteBySchedule,
    cancelBulkDeleteConfirm,
    confirmBulkDeleteBySchedule,
  };
}
