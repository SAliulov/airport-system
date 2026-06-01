import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  assignAircraft,
  assignGate,
  correctActualTimes,
  createDelayWarning,
  createFlight,
  deleteFlight,
  generateFlights,
  getAircraftTypes,
  getAirlines,
  getAllFlights,
  getFilteredFlights,
  getFlightById,
  getGates,
  getSchedules,
  searchFlights,
  updateFlightStatus,
} from '../services/api';
import PageStatus from '../components/PageStatus';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { useStomp } from '../hooks/useStomp';
import { formatApiError } from '../utils/apiError';
import type {
  AircraftTypeRs,
  AirlineRs,
  FlightRs,
  FlightStatus,
  GateAssignmentRs,
  GateRs,
  GateSummaryRs,
  ScheduleRs,
  ScheduleSlotRs,
  SizeCategory,
} from '../types';
import {
  formatAirportDateTime,
  formatAirportTime,
  fromDatetimeLocalValue,
  isoDayOfWeekLabel,
  todayAirportDate,
  toDatetimeLocalValue,
} from '../utils/airportTime';

interface CreateSuccessBanner {
  flightId: number;
  label: string;
  showDate: string;
}

const HOME_IATA = 'SVO';

const SIZE_RANK: Record<SizeCategory, number> = { NARROW: 0, WIDE: 1, JUMBO: 2 };

/** Та же логика, что SizeCategory.isCompatible на бэкенде. */
function isAircraftFitsGate(aircraftSize?: SizeCategory, gateMax?: SizeCategory): boolean {
  if (!aircraftSize || !gateMax) return true;
  return SIZE_RANK[aircraftSize] <= SIZE_RANK[gateMax];
}

const FILTER_STATUSES: FlightStatus[] = ['SCHEDULED', 'DEPARTED', 'ARRIVED', 'DELAYED', 'CANCELLED'];
const WS_TOPICS = ['/topic/flights', '/topic/delays', '/topic/gate-changes'];

const RULES_TOOLTIP =
  'Изменять расписание, гейт и тип ВС можно только для SCHEDULED и DELAYED. ' +
  'Для отменённого (CANCELLED), вылетевшего (DEPARTED) и прибывшего (ARRIVED) рейса параметры закрыты. ' +
  'Переход в DEPARTED требует фактическое время вылета, тип ВС и гейт SVO. ' +
  'Переход в ARRIVED — фактическое время прилёта и гейт SVO. ' +
  'Предупреждение о задержке — только при статусе DELAYED (выберите DELAYED или рейс уже задержан). ' +
  'Рейс ARRIVED — можно скорректировать только фактические времена вылета и прилёта. ' +
  'Удаление — только SCHEDULED и CANCELLED. ' +
  'Сортировка: плановый вылет ↑.';

function isActualTimesOnlyMode(status: FlightStatus): boolean {
  return status === 'ARRIVED';
}

function isClosedFlightStatus(status: FlightStatus): boolean {
  return status === 'DEPARTED' || status === 'ARRIVED' || status === 'CANCELLED';
}

function canDeleteFlight(status: FlightStatus): boolean {
  return status === 'SCHEDULED' || status === 'CANCELLED';
}

function formatAircraftCell(flight: FlightRs): string {
  const ac = flight.aircraftType;
  if (!ac?.icaoCode) return 'Не назначен';
  const cat = ac.sizeCategory ? ` [${ac.sizeCategory}]` : '';
  return `${ac.icaoCode}${cat}`;
}

function formatGateLabel(g: GateSummaryRs): string {
  const term = g.terminal ? ` (${g.terminal})` : '';
  const cat = g.maxSizeCategory ? ` — [${g.maxSizeCategory}]` : '';
  return `${g.gateNumber}${term}${cat}`;
}

function formatGateInterval(assignment?: GateAssignmentRs): string | null {
  if (!assignment?.assignedFrom && !assignment?.assignedTo) return null;
  return `${formatAirportDateTime(assignment.assignedFrom)} – ${formatAirportDateTime(assignment.assignedTo)}`;
}

function flightDirection(schedule?: ScheduleRs): 'outbound' | 'inbound' | null {
  const o = (schedule?.originAirport ?? '').trim().toUpperCase();
  const d = (schedule?.destinationAirport ?? '').trim().toUpperCase();
  if (o === HOME_IATA && d !== HOME_IATA) return 'outbound';
  if (d === HOME_IATA && o !== HOME_IATA) return 'inbound';
  return null;
}

function allowedStatusOptions(detail: FlightRs): FlightStatus[] {
  const dir = flightDirection(detail.schedule);
  const s = detail.status;
  if (dir === 'outbound') {
    const opts: FlightStatus[] = [];
    if (s === 'SCHEDULED') {
      opts.push('DELAYED', 'DEPARTED', 'CANCELLED');
    }
    if (s === 'DELAYED') {
      opts.push('DEPARTED', 'CANCELLED');
    }
    if (s === 'DEPARTED') opts.push('ARRIVED');
    return opts;
  }
  if (dir === 'inbound') {
    const opts: FlightStatus[] = [];
    if (s === 'SCHEDULED') {
      opts.push('DELAYED', 'DEPARTED', 'CANCELLED');
    }
    if (s === 'DELAYED') {
      opts.push('DEPARTED', 'CANCELLED', 'ARRIVED');
    }
    if (s === 'DEPARTED') opts.push('ARRIVED');
    return opts;
  }
  if (s === 'SCHEDULED' || s === 'DELAYED') return ['CANCELLED'];
  return [];
}

function gateFormFromAssignment(assignment?: GateAssignmentRs) {
  if (!assignment?.gate?.gateId) {
    return { gateId: '', assignedFrom: '', assignedTo: '' };
  }
  return {
    gateId: String(assignment.gate.gateId),
    assignedFrom: toDatetimeLocalValue(assignment.assignedFrom),
    assignedTo: toDatetimeLocalValue(assignment.assignedTo),
  };
}

function gateFormFromFlight(detail: FlightRs) {
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

function gateAssignmentChanged(
  form: { gateId: string; assignedFrom: string; assignedTo: string },
  current?: GateAssignmentRs,
): boolean {
  if (!form.gateId) return false;
  if (!current?.gate?.gateId) return true;
  const baseline = gateFormFromAssignment(current);
  return (
    form.gateId !== baseline.gateId
    || form.assignedFrom !== baseline.assignedFrom
    || form.assignedTo !== baseline.assignedTo
  );
}

function flightScheduleLabel(flight: FlightRs): string {
  const s = flight.schedule;
  if (!s) return '—';
  const dayPart = flight.operationDate ? `${flight.operationDate} ` : '';
  return `${dayPart}${s.flightNumber} ${(s.originAirport ?? '').trim()} → ${(s.destinationAirport ?? '').trim()} (${formatAirportDateTime(flight.scheduledDeparture)})`;
}

function slotOptionLabel(schedule: ScheduleRs, slot: ScheduleSlotRs): string {
  const dow = schedule.periodicityType === 'INTERVAL' ? '' : `${isoDayOfWeekLabel(slot.dayOfWeek)} `;
  return `${schedule.flightNumber} ${(schedule.originAirport ?? '').trim()}→${(schedule.destinationAirport ?? '').trim()} ${dow}${formatAirportTime(slot.departureTime)}–${formatAirportTime(slot.arrivalTime)}`;
}

function GateTableCell({ flight }: { flight: FlightRs }) {
  const assignment = flight.currentGateAssignment;
  const g = assignment?.gate;
  if (!g?.gateNumber) return <td>Не назначен</td>;
  const interval = formatGateInterval(assignment);
  return (
    <td className="gate-cell" title={interval ?? undefined}>
      {formatGateLabel(g)}
    </td>
  );
}

function gateOptionLabel(gate: GateRs): string {
  const term = gate.terminal ? ` (${gate.terminal})` : '';
  const cat = gate.maxSizeCategory ? ` — [${gate.maxSizeCategory}]` : '';
  return `${gate.gateNumber}${term}${cat}`;
}

export default function FlightsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialDate = searchParams.get('date') || todayAirportDate();
  const initialSearch = searchParams.get('search')?.toUpperCase() ?? '';
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [schedules, setSchedules] = useState<ScheduleRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [aircraftTypes, setAircraftTypes] = useState<AircraftTypeRs[]>([]);
  const [gates, setGates] = useState<GateRs[]>([]);
  const [pageError, setPageError] = useState<string | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);

  const [filterDate, setFilterDate] = useState(initialDate);
  const [filterStatus, setFilterStatus] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [filterOrigin, setFilterOrigin] = useState('');
  const [filterDestination, setFilterDestination] = useState('');
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const debouncedSearch = useDebouncedValue(searchQuery.trim(), 400);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDetail, setEditDetail] = useState<FlightRs | null>(null);

  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [modalScheduleFilter, setModalScheduleFilter] = useState('');
  const [modalSlotId, setModalSlotId] = useState('');
  const [modalOperationDate, setModalOperationDate] = useState('');
  const [createSaving, setCreateSaving] = useState(false);
  const [createSuccess, setCreateSuccess] = useState<CreateSuccessBanner | null>(null);

  const [generateModalOpen, setGenerateModalOpen] = useState(false);
  const [generateFrom, setGenerateFrom] = useState('');
  const [generateTo, setGenerateTo] = useState('');
  const [generateScheduleId, setGenerateScheduleId] = useState('');
  const [generateSaving, setGenerateSaving] = useState(false);
  const [generateInfo, setGenerateInfo] = useState<string | null>(null);

  const [statusNew, setStatusNew] = useState('');
  const [statusActualDeparture, setStatusActualDeparture] = useState('');
  const [statusActualArrival, setStatusActualArrival] = useState('');
  const [acType, setAcType] = useState('');
  const [gateForm, setGateForm] = useState({ gateId: '', assignedFrom: '', assignedTo: '' });
  const [delayForm, setDelayForm] = useState({ delayMinutes: '', reason: '' });
  const [editSaving, setEditSaving] = useState(false);

  const editingDetailRef = useRef<FlightRs | null>(null);

  function syncEditFormFromDetail(detail: FlightRs, clearPending = true) {
    setStatusActualDeparture(toDatetimeLocalValue(detail.actualDeparture));
    setStatusActualArrival(toDatetimeLocalValue(detail.actualArrival));
    setAcType(detail.aircraftType ? String(detail.aircraftType.aircraftTypeId) : '');
    setGateForm(gateFormFromFlight(detail));
    if (clearPending) {
      setStatusNew('');
      setDelayForm({ delayMinutes: '', reason: '' });
    }
  }

  useEffect(() => {
    editingDetailRef.current = editDetail;
  }, [editDetail]);

  const load = useCallback((dateOverride?: string) => {
    setPageError(null);
    const effectiveDate = dateOverride ?? filterDate;
    const params: Record<string, string> = {};
    if (effectiveDate) params.date = effectiveDate;
    if (filterStatus) params.status = filterStatus;
    if (filterAirline) params.airline = filterAirline;
    if (filterOrigin.length === 3) params.origin = filterOrigin;
    if (filterDestination.length === 3) params.destination = filterDestination;

    const request = debouncedSearch
      ? searchFlights(debouncedSearch, params)
      : Object.keys(params).length > 0
        ? getFilteredFlights(params)
        : getAllFlights();

    request
      .then(setFlights)
      .catch(e => setPageError(formatApiError(e)));
  }, [filterAirline, filterDate, filterDestination, filterOrigin, filterStatus, debouncedSearch]);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    getAirlines().then(setAirlines).catch(e => setPageError(formatApiError(e)));
    getAircraftTypes().then(setAircraftTypes).catch(e => setPageError(formatApiError(e)));
    getGates().then(setGates).catch(e => setPageError(formatApiError(e)));
    getSchedules().then(setSchedules).catch(e => setPageError(formatApiError(e)));
  }, []);

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

  async function openCreateModal() {
    setModalError(null);
    setModalScheduleFilter('');
    setModalSlotId('');
    setModalOperationDate(filterDate || todayAirportDate());
    try {
      const list = await getSchedules();
      setSchedules(list);
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

  function closeGenerateModal() {
    setGenerateModalOpen(false);
    setGenerateInfo(null);
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
    setGenerateSaving(true);
    setGenerateInfo(null);
    try {
      const body: { fromDate: string; toDate: string; scheduleId?: number } = {
        fromDate: generateFrom,
        toDate: generateTo,
      };
      if (generateScheduleId) body.scheduleId = Number(generateScheduleId);
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

  const handleWsMessage = useCallback((body: string, topic: string) => {
    try {
      const msg = JSON.parse(body) as Record<string, unknown>;
      if (topic === '/topic/flights' && msg.flightId) {
        const fid = Number(msg.flightId);
        setFlights(prev =>
          prev.map(fl =>
            fl.flightId === fid
              ? {
                  ...fl,
                  status: (msg.status as FlightStatus | undefined) ?? fl.status,
                  actualDeparture: (msg.actualDeparture as string | undefined) ?? fl.actualDeparture,
                  actualArrival: (msg.actualArrival as string | undefined) ?? fl.actualArrival,
                }
              : fl,
          ),
        );
      } else if (topic === '/topic/gate-changes' && msg.flightId) {
        const fid = Number(msg.flightId);
        setFlights(prev =>
          prev.map(fl =>
            fl.flightId === fid
              ? {
                  ...fl,
                  currentGateAssignment:
                    (msg.assignment as FlightRs['currentGateAssignment']) ?? fl.currentGateAssignment,
                }
              : fl,
          ),
        );
      }

      if (msg.flightId && editingDetailRef.current?.flightId === Number(msg.flightId)) {
        getFlightById(Number(msg.flightId))
          .then(detail => {
            setEditDetail(detail);
            syncEditFormFromDetail(detail, false);
          })
          .catch(() => {});
      }
    } catch {
      // ignore malformed websocket payloads
    }
  }, []);

  useStomp(WS_TOPICS, handleWsMessage);

  async function startEdit(flight: FlightRs) {
    setModalError(null);
    setEditingId(flight.flightId);
    try {
      const detail = await getFlightById(flight.flightId);
      setEditDetail(detail);
      syncEditFormFromDetail(detail);
      if (!isActualTimesOnlyMode(detail.status)) {
        const [allGates, allTypes] = await Promise.all([getGates(), getAircraftTypes()]);
        setGates(allGates);
        setAircraftTypes(allTypes);
      }
    } catch (e: unknown) {
      setEditingId(null);
      setEditDetail(null);
      setPageError(formatApiError(e));
    }
  }

  function cancelEdit() {
    setModalError(null);
    setEditingId(null);
    setEditDetail(null);
    setStatusNew('');
    setStatusActualDeparture('');
    setStatusActualArrival('');
    setAcType('');
    setGateForm({ gateId: '', assignedFrom: '', assignedTo: '' });
    setDelayForm({ delayMinutes: '', reason: '' });
  }

  function showCreatedFlightInList(showDate: string) {
    setFilterDate(showDate);
    setCreateSuccess(null);
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('date', showDate);
      return next;
    });
    load(showDate);
  }

  async function submitCreateFlight() {
    const slotId = Number(modalSlotId);
    if (!modalSlotId || !Number.isInteger(slotId) || slotId <= 0) {
      setModalError('Выберите слот из списка.');
      return;
    }
    if (!modalOperationDate) {
      setModalError('Укажите дату операции.');
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

  async function removeFlight(id: number, status: FlightStatus) {
    if (!canDeleteFlight(status)) return;
    if (!confirm('Удалить рейс?')) return;
    try {
      await deleteFlight(id);
      if (editingId === id) cancelEdit();
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  async function applyEditChanges() {
    if (!editDetail) return;

    const flightId = editDetail.flightId;

    if (isActualTimesOnlyMode(editDetail.status)) {
      const depChanged = statusActualDeparture !== toDatetimeLocalValue(editDetail.actualDeparture);
      const arrChanged = statusActualArrival !== toDatetimeLocalValue(editDetail.actualArrival);
      if (!depChanged && !arrChanged) {
        setModalError('Нет изменений для сохранения. Измените фактические времена и нажмите «Применить».');
        return;
      }
      setEditSaving(true);
      setModalError(null);
      try {
        const body: { actualDeparture?: string; actualArrival?: string } = {};
        if (depChanged) {
          const actualDeparture = fromDatetimeLocalValue(statusActualDeparture);
          if (!actualDeparture) {
            setModalError('Укажите корректное фактическое время вылета.');
            setEditSaving(false);
            return;
          }
          body.actualDeparture = actualDeparture;
        }
        if (arrChanged) {
          const actualArrival = fromDatetimeLocalValue(statusActualArrival);
          if (!actualArrival) {
            setModalError('Укажите корректное фактическое время прилёта.');
            setEditSaving(false);
            return;
          }
          body.actualArrival = actualArrival;
        }
        await correctActualTimes(flightId, body);
        const detail = await getFlightById(flightId);
        setEditDetail(detail);
        syncEditFormFromDetail(detail);
        load();
      } catch (e: unknown) {
        setModalError(formatApiError(e));
      } finally {
        setEditSaving(false);
      }
      return;
    }

    const currentAircraftId = editDetail.aircraftType?.aircraftTypeId;
    const closed = isClosedFlightStatus(editDetail.status);

    const aircraftChanged =
      !closed && acType && Number(acType) !== currentAircraftId;
    const gatePending =
      !closed && gateAssignmentChanged(gateForm, editDetail.currentGateAssignment);
    const statusPending = Boolean(statusNew);
    const delayFieldsActive =
      statusNew === 'DELAYED' || (editDetail.status === 'DELAYED' && !statusNew);
    const delayPending = delayFieldsActive && Boolean(delayForm.delayMinutes);

    if (!aircraftChanged && !gatePending && !statusPending && !delayPending) {
      setModalError('Нет изменений для сохранения. Измените поля в форме и нажмите «Применить».');
      return;
    }

    if (statusPending) {
      if (statusNew === 'DEPARTED' && !statusActualDeparture) {
        setModalError('Укажите фактическое время вылета для перевода в DEPARTED.');
        return;
      }
      if (statusNew === 'ARRIVED' && !statusActualArrival) {
        setModalError('Укажите фактическое время прилёта для перевода в ARRIVED.');
        return;
      }
      if (statusNew === 'DELAYED' && !delayForm.delayMinutes) {
        setModalError('При переводе в DELAYED укажите минуты задержки и причину.');
        return;
      }
    }

    setEditSaving(true);
    setModalError(null);
    try {
      if (aircraftChanged) {
        await assignAircraft(flightId, Number(acType));
      }
      if (gatePending) {
        const from = fromDatetimeLocalValue(gateForm.assignedFrom);
        const to = fromDatetimeLocalValue(gateForm.assignedTo);
        if (!from || !to) {
          setModalError('Укажите корректный интервал занятости гейта (с / по), формат даты YYYY-MM-DDTHH:mm.');
          setEditSaving(false);
          return;
        }
        await assignGate(flightId, {
          gateId: Number(gateForm.gateId),
          assignedFrom: from,
          assignedTo: to,
        });
      }
      if (statusPending) {
        const body: { status: string; actualDeparture?: string; actualArrival?: string } = {
          status: statusNew,
        };
        if (statusNew === 'DEPARTED') {
          const actualDeparture = fromDatetimeLocalValue(statusActualDeparture);
          if (!actualDeparture) {
            setModalError('Укажите корректное фактическое время вылета для перевода в DEPARTED.');
            setEditSaving(false);
            return;
          }
          body.actualDeparture = actualDeparture;
        }
        if (statusNew === 'ARRIVED') {
          const actualArrival = fromDatetimeLocalValue(statusActualArrival);
          if (!actualArrival) {
            setModalError('Укажите корректное фактическое время прилёта для перевода в ARRIVED.');
            setEditSaving(false);
            return;
          }
          body.actualArrival = actualArrival;
        }
        await updateFlightStatus(flightId, body);
      }
      if (delayPending) {
        await createDelayWarning(flightId, {
          delayMinutes: Number(delayForm.delayMinutes),
          reason: delayForm.reason || undefined,
        });
      }

      const detail = await getFlightById(flightId);
      setEditDetail(detail);
      syncEditFormFromDetail(detail);
      load();
    } catch (e: unknown) {
      setModalError(formatApiError(e));
    } finally {
      setEditSaving(false);
    }
  }

  const statusColor: Record<string, string> = {
    SCHEDULED: '#3b82f6',
    DEPARTED: '#f97316',
    ARRIVED: '#22c55e',
    DELAYED: '#ef4444',
    CANCELLED: '#6b7280',
  };

  const locks = editDetail ? isClosedFlightStatus(editDetail.status) : false;
  const actualTimesOnly = editDetail ? isActualTimesOnlyMode(editDetail.status) : false;
  const showDelayFields = Boolean(
    editDetail && (statusNew === 'DELAYED' || (editDetail.status === 'DELAYED' && !statusNew)),
  );

  const selectedAircraftSize: SizeCategory | undefined =
    (acType ? aircraftTypes.find(t => String(t.aircraftTypeId) === acType)?.sizeCategory : undefined)
    ?? editDetail?.aircraftType?.sizeCategory;

  const selectedGateMax: SizeCategory | undefined =
    (gateForm.gateId
      ? gates.find(g => String(g.gateId) === gateForm.gateId)?.maxSizeCategory
      : undefined)
    ?? editDetail?.currentGateAssignment?.gate?.maxSizeCategory;

  return (
    <div className="page flights-layout">
      <h1>
        Рейсы
        <span className="rules-info" title={RULES_TOOLTIP} aria-label="Правила блокировок по статусу">
          ⓘ
        </span>
      </h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />

      {createSuccess && (
        <div className="page-success-banner" role="status">
          Рейс #{createSuccess.flightId} создан ({createSuccess.label}).
          {createSuccess.showDate !== filterDate ? (
            <>
              {' '}
              <button
                type="button"
                className="btn-link"
                onClick={() => showCreatedFlightInList(createSuccess.showDate)}
              >
                Показать в списке
              </button>
            </>
          ) : (
            <span> Отображается в таблице ниже.</span>
          )}
          <button
            type="button"
            className="btn-ghost btn-sm"
            style={{ marginLeft: 8 }}
            onClick={() => setCreateSuccess(null)}
            aria-label="Закрыть"
          >
            ✕
          </button>
        </div>
      )}

      {createModalOpen && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="flight-create-title">
          <div className="modal-card">
            <h3 id="flight-create-title">Добавить новый рейс</h3>
            {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
            <p className="muted" style={{ fontSize: 13, marginBottom: 8 }}>
              Выберите слот шаблона и дату операции — рейс будет создан на эту дату.
            </p>
            <label>
              Шаблон (фильтр)
              <select
                value={modalScheduleFilter}
                onChange={e => {
                  setModalScheduleFilter(e.target.value);
                  setModalSlotId('');
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
              <select
                value={modalSlotId}
                onChange={e => setModalSlotId(e.target.value)}
              >
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
                onChange={e => setModalOperationDate(e.target.value)}
              />
            </label>
            {availableSlots.length === 0 && (
              <p className="modal-alert">Нет доступных слотов.</p>
            )}
            <div className="modal-actions modal-actions--center">
              <button type="button" className="btn-ghost" onClick={closeCreateModal} disabled={createSaving}>
                Отмена
              </button>
              <button
                type="button"
                className="btn-primary"
                disabled={createSaving || availableSlots.length === 0}
                onClick={() => void submitCreateFlight()}
              >
                {createSaving ? 'Создание…' : 'Создать'}
              </button>
            </div>
          </div>
        </div>
      )}

      {generateModalOpen && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="flight-generate-title">
          <div className="modal-card">
            <h3 id="flight-generate-title">Генерация рейсов</h3>
            <p className="modal-hint">
              Создаёт рейсы из активных шаблонов за указанный период. Список рейсов фильтруется по одному
              дню — после успешной генерации фильтр сбрасывается автоматически.
            </p>
            {generateInfo && <p className="modal-alert" role="status">{generateInfo}</p>}
            <label>
              С
              <input type="date" value={generateFrom} onChange={e => setGenerateFrom(e.target.value)} />
            </label>
            <label>
              По
              <input type="date" value={generateTo} onChange={e => setGenerateTo(e.target.value)} />
            </label>
            <label>
              Шаблон (необязательно)
              <select value={generateScheduleId} onChange={e => setGenerateScheduleId(e.target.value)}>
                <option value="">— все активные шаблоны —</option>
                {schedules.filter(s => s.isActive !== false).map(s => (
                  <option key={s.scheduleId} value={String(s.scheduleId)}>
                    {s.flightNumber} {s.originAirport.trim()}→{s.destinationAirport.trim()}
                  </option>
                ))}
              </select>
            </label>
            <div className="modal-actions modal-actions--center">
              <button type="button" className="btn-ghost" onClick={closeGenerateModal} disabled={generateSaving}>
                Закрыть
              </button>
              <button
                type="button"
                className="btn-primary"
                disabled={generateSaving}
                onClick={() => void submitGenerate()}
              >
                {generateSaving ? 'Генерация…' : 'Сгенерировать'}
              </button>
            </div>
          </div>
        </div>
      )}

      {editingId != null && editDetail && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="flight-edit-title"
          onClick={e => {
            if (e.target === e.currentTarget) cancelEdit();
          }}
        >
          <div className="modal-card modal-card--edit" onClick={e => e.stopPropagation()}>
            <h3 id="flight-edit-title">
              {actualTimesOnly ? 'Коррекция времени' : 'Редактирование'} рейса #{editDetail.flightId} — {editDetail.schedule?.flightNumber ?? '—'}
            </h3>
            {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
            <p className="modal-hint">
              {actualTimesOnly
                ? 'Исправьте фактическое время вылета и/или прилёта. Остальные параметры рейса закрыты.'
                : 'Заполните нужные поля и нажмите «Применить изменения». Данные уйдут на сервер, таблица обновится автоматически.'}
            </p>
            <div className="modal-card__body">

              <section className="panel-section" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 12 }}>
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>Расписание</h3>
                <p className="modal-readonly">
                  {editDetail ? flightScheduleLabel(editDetail) : '—'}
                </p>
                <p className="modal-hint">Слот расписания нельзя изменить — создайте новый рейс при необходимости.</p>
              </section>

              {actualTimesOnly ? (
              <section className="panel-section">
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>
                  Фактические времена
                </h3>
                <label style={{ display: 'block', marginBottom: 8 }}>
                  Фактическое время вылета
                  <input
                    type="datetime-local"
                    value={statusActualDeparture}
                    onChange={e => setStatusActualDeparture(e.target.value)}
                    style={{ width: '100%' }}
                  />
                </label>
                <label style={{ display: 'block' }}>
                  Фактическое время прилёта
                  <input
                    type="datetime-local"
                    value={statusActualArrival}
                    onChange={e => setStatusActualArrival(e.target.value)}
                    style={{ width: '100%' }}
                  />
                </label>
                <p style={{ marginTop: 8 }}>
                  Статус:{' '}
                  <strong style={{ color: statusColor[editDetail.status] ?? '#fff' }}>{editDetail.status}</strong>
                </p>
              </section>
              ) : (
              <>
              <section className="panel-section" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 12 }}>
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>Статус</h3>
                <select value={statusNew} onChange={e => setStatusNew(e.target.value)} style={{ width: '100%' }}>
                  <option value="">— не менять —</option>
                  {allowedStatusOptions(editDetail).map(status => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
                <p style={{ fontSize: 11, color: 'var(--muted)', marginTop: 6 }}>
                  Вылет/прилёт в SVO — вручную; задержка outbound и автопереходы — планировщик (раз в ~1 мин).
                </p>
                {flightDirection(editDetail.schedule) === 'inbound' && !editDetail.aircraftType && (
                  <p style={{ fontSize: 11, color: '#f59e0b', marginTop: 6 }}>
                    Без типа ВС планировщик переведёт рейс в DELAYED после планового вылета + 5 мин;
                    автовылет (DEPARTED) — только после назначения типа ВС.
                  </p>
                )}
            {statusNew === 'DEPARTED' && (
              <label style={{ marginTop: 8, display: 'block' }}>
                Фактическое время вылета
                <input
                  type="datetime-local"
                  value={statusActualDeparture}
                  onChange={e => setStatusActualDeparture(e.target.value)}
                />
              </label>
            )}
            {statusNew === 'ARRIVED' && (
              <label style={{ marginTop: 8, display: 'block' }}>
                Фактическое время прилёта
                <input
                  type="datetime-local"
                  value={statusActualArrival}
                  onChange={e => setStatusActualArrival(e.target.value)}
                />
              </label>
            )}
            <p style={{ marginTop: 8 }}>
              Текущий:{' '}
              <strong style={{ color: statusColor[editDetail.status] ?? '#fff' }}>{editDetail.status}</strong>
            </p>
          </section>

              <section className="panel-section" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 12 }}>
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>Тип ВС</h3>
                <select
                  value={acType}
                  onChange={e => setAcType(e.target.value)}
                  disabled={locks}
                  title={locks ? 'Ресурсы закрыты для этого статуса рейса' : undefined}
                  style={{ width: '100%' }}
                >
                  <option value="">— не менять —</option>
                  {aircraftTypes.map(type => {
                    const disabled = !isAircraftFitsGate(type.sizeCategory, selectedGateMax);
                    return (
                      <option
                        key={type.aircraftTypeId}
                        value={String(type.aircraftTypeId)}
                        disabled={disabled}
                      >
                        {type.icaoCode} ({type.sizeCategory ?? '?'})
                        {disabled ? ' — не подходит к гейту' : ''}
                      </option>
                    );
                  })}
                </select>
            <p style={{ marginTop: 8 }}>
              Текущий: <strong>{editDetail.aircraftType?.icaoCode ?? '—'}</strong>
            </p>
          </section>

              <section className="panel-section" style={{ borderBottom: '1px solid var(--border)', paddingBottom: 12 }}>
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>Гейт</h3>
                {editDetail.currentGateAssignment?.gate?.gateId ? (
                  <div
                    style={{
                      marginBottom: 12,
                      padding: '10px 12px',
                      background: 'var(--surface2)',
                      borderRadius: 8,
                      fontSize: 13,
                    }}
                  >
                    <div style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 4 }}>Текущее назначение</div>
                    <strong>{formatGateLabel(editDetail.currentGateAssignment.gate)}</strong>
                    {formatGateInterval(editDetail.currentGateAssignment) && (
                      <div style={{ marginTop: 4, color: 'var(--muted)' }}>
                        {formatGateInterval(editDetail.currentGateAssignment)}
                      </div>
                    )}
                  </div>
                ) : (
                  <p style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 12 }}>Гейт не назначен</p>
                )}
                <p style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 8 }}>
                  {editDetail.currentGateAssignment?.gate?.gateId
                    ? 'Изменить гейт или интервал занятости'
                    : 'Назначить гейт (интервал по умолчанию — плановые вылет/прилёт)'}
                </p>
                <select
                  value={gateForm.gateId}
                  disabled={locks}
                  onChange={e => setGateForm(prev => ({ ...prev, gateId: e.target.value }))}
                  title={locks ? 'Ресурсы закрыты для этого статуса рейса' : undefined}
                  style={{ width: '100%', marginBottom: 8 }}
                >
                  <option value="">— не назначать —</option>
                  {gates.filter(gate => gate.isActive).map(gate => {
                    const disabled = !isAircraftFitsGate(selectedAircraftSize, gate.maxSizeCategory);
                    return (
                      <option
                        key={gate.gateId}
                        value={String(gate.gateId)}
                        disabled={disabled}
                      >
                        {gateOptionLabel(gate)}
                        {disabled ? ' — не подходит к типу ВС' : ''}
                      </option>
                    );
                  })}
                </select>
                <p style={{ fontSize: 11, color: 'var(--muted)', margin: '8px 0 6px' }}>
                  Интервал занятости гейта (с / по), время аэропорта SVO
                </p>
                <div className="form-row" style={{ flexWrap: 'wrap', gap: 8 }}>
                  <label>
                    С
                    <input
                      type="datetime-local"
                      disabled={locks}
                      value={gateForm.assignedFrom}
                      onChange={e => setGateForm(prev => ({ ...prev, assignedFrom: e.target.value }))}
                    />
                  </label>
                  <label>
                    По
                    <input
                      type="datetime-local"
                      disabled={locks}
                      value={gateForm.assignedTo}
                      onChange={e => setGateForm(prev => ({ ...prev, assignedTo: e.target.value }))}
                    />
                  </label>
                </div>
          </section>

              {showDelayFields && (
              <section className="panel-section">
                <h3 style={{ fontSize: 12, textTransform: 'uppercase', color: 'var(--muted)', marginBottom: 8 }}>
                  Задержка (новое предупреждение)
                </h3>
                <p style={{ fontSize: 11, color: 'var(--muted)', marginBottom: 8 }}>
                  {statusNew === 'DELAYED'
                    ? 'Укажите минуты и причину — сохранятся вместе с переводом в DELAYED.'
                    : 'Рейс уже DELAYED — можно добавить текст задержки.'}
                </p>
                <div className="form-row" style={{ flexWrap: 'wrap', gap: 8 }}>
                  <input
                    placeholder="Минуты"
                    type="number"
                    min={1}
                    style={{ width: 90 }}
                    value={delayForm.delayMinutes}
                    onChange={e => setDelayForm(prev => ({ ...prev, delayMinutes: e.target.value }))}
                  />
                  <input
                    placeholder="Причина"
                    style={{ flex: 1, minWidth: 160 }}
                    value={delayForm.reason}
                    onChange={e => setDelayForm(prev => ({ ...prev, reason: e.target.value }))}
                  />
                </div>
                {(editDetail.delayWarnings ?? []).length === 0 ? (
                  <p className="muted" style={{ marginTop: 8 }}>Нет записей о задержке</p>
                ) : (
                  (editDetail.delayWarnings ?? []).map(warning => (
                    <div key={warning.warningId} className="delay-row" style={{ marginTop: 6 }}>
                      ! {warning.delayMinutes} мин — {warning.reason ?? 'причина не указана'}
                    </div>
                  ))
                )}
              </section>
              )}
              </>
              )}
            </div>

            <div className="modal-actions modal-actions--footer">
              <button type="button" className="btn-ghost" onClick={cancelEdit} disabled={editSaving}>
                Закрыть
              </button>
              <button
                type="button"
                className="btn-primary"
                disabled={editSaving}
                onClick={() => void applyEditChanges()}
              >
                {editSaving ? 'Сохранение…' : 'Применить изменения'}
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="flights-list">
        <div className="form-row" style={{ flexWrap: 'wrap', gap: 8 }}>
          <input
            type="date"
            value={filterDate}
            title="День планового вылета (Europe/Moscow)"
            onChange={e => setFilterDate(e.target.value)}
          />
          <button
            type="button"
            className="btn-ghost btn-sm"
            title="Сбросить фильтр по дате"
            onClick={() => setFilterDate('')}
          >
            Все даты
          </button>
          <button
            type="button"
            className="btn-ghost btn-sm"
            onClick={() => setFilterDate(todayAirportDate())}
          >
            Сегодня
          </button>
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
            <option value="">Все статусы</option>
            {FILTER_STATUSES.map(status => (
              <option key={status} value={status}>{status}</option>
            ))}
          </select>
          <select value={filterAirline} onChange={e => setFilterAirline(e.target.value)}>
            <option value="">Все авиакомпании</option>
            {airlines.map(airline => (
              <option key={airline.airlineId} value={String(airline.airlineId)}>
                {airline.iataCode} - {airline.name}
              </option>
            ))}
          </select>
          <input
            placeholder="Откуда (IATA)"
            maxLength={3}
            style={{ width: 110 }}
            value={filterOrigin}
            onChange={e => setFilterOrigin(e.target.value.toUpperCase())}
          />
          <input
            placeholder="Куда (IATA)"
            maxLength={3}
            style={{ width: 110 }}
            value={filterDestination}
            onChange={e => setFilterDestination(e.target.value.toUpperCase())}
          />
          <input
            placeholder="Поиск по номеру"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value.toUpperCase())}
          />
          <button type="button" className="btn-primary btn-sm" onClick={() => void openCreateModal()}>
            Добавить новый рейс
          </button>
          <button type="button" className="btn-ghost btn-sm" onClick={openGenerateModal}>
            Сгенерировать рейсы
          </button>
          <p className="filter-date-hint">
            {filterDate
              ? `Показаны рейсы с плановым вылетом ${filterDate} (MSK). Скрыты другие дни.`
              : 'Все даты: без фильтра по дню планового вылета.'}
          </p>
        </div>

        <table className="data-table" style={{ marginTop: 16 }}>
          <thead>
            <tr>
              <th>Рейс</th>
              <th>Откуда</th>
              <th>Куда</th>
              <th>План вылет</th>
              <th>План прилёт</th>
              <th>Авиакомпания</th>
              <th>Факт вылет</th>
              <th>Факт прилёт</th>
              <th>Статус</th>
              <th>Тип ВС</th>
              <th>Гейт</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {flights.map(flight => (
              <tr
                key={flight.flightId}
                className={editingId === flight.flightId ? 'row-selected' : ''}
              >
                <td>{flight.schedule?.flightNumber ?? '—'}</td>
                <td>{(flight.schedule?.originAirport ?? '').trim() || '—'}</td>
                <td>{(flight.schedule?.destinationAirport ?? '').trim() || '—'}</td>
                <td>{formatAirportDateTime(flight.scheduledDeparture)}</td>
                <td>{formatAirportDateTime(flight.scheduledArrival)}</td>
                <td>{flight.schedule?.airline?.name ?? '—'}</td>
                <td>{formatAirportDateTime(flight.actualDeparture)}</td>
                <td>{formatAirportDateTime(flight.actualArrival)}</td>
                <td>
                  <span style={{ color: statusColor[flight.status] ?? '#fff', fontWeight: 600 }}>
                    {flight.status}
                  </span>
                </td>
                <td>{formatAircraftCell(flight)}</td>
                <GateTableCell flight={flight} />
                <td className="cell-actions">
                  <button
                    type="button"
                    className="btn-ghost btn-sm"
                    title={
                      flight.status === 'ARRIVED'
                        ? 'Скорректировать фактические времена'
                        : 'Редактировать'
                    }
                    onClick={() => void startEdit(flight)}
                    aria-label="Редактировать"
                  >
                    ✏
                  </button>
                  <button
                    type="button"
                    className="btn-danger btn-sm"
                    disabled={!canDeleteFlight(flight.status)}
                    title={
                      !canDeleteFlight(flight.status)
                        ? 'Удаление только для SCHEDULED или CANCELLED'
                        : 'Удалить рейс'
                    }
                    onClick={() => void removeFlight(flight.flightId, flight.status)}
                  >
                    ✕
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
