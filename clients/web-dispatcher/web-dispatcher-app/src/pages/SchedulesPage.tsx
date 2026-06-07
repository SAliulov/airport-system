import { useCallback, useEffect, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import {
  createSchedule,
  deleteSchedule,
  getAirlines,
  getFilteredSchedules,
  getSchedules,
  searchSchedules,
  updateSchedule,
} from '../services/api';
import type { AirlineRs, PeriodicityType, ScheduleRs, ScheduleSlotRs } from '../types';
import { formatApiError } from '../utils/apiError';
import {
  formatAirportDateTime,
  formatAirportTime,
  getAirportTimezone,
  isoDayOfWeekLabel,
  todayAirportDate,
} from '../utils/airportTime';
import {
  mapScheduleBackendError,
  validateScheduleForm,
  type ScheduleSlotFormValues,
} from '../utils/fieldValidation';
import {
  buildSchedulePayload,
  EMPTY_SCHEDULE_FORM as EMPTY,
  DEFAULT_SCHEDULE_SLOT as DEFAULT_SLOT,
  scheduleToForm,
} from '../features/schedules/scheduleFormMappers';
import { ScheduleFormModal } from '../features/schedules/components/ScheduleFormModal';

function formatSlotSummary(slot: ScheduleSlotRs, periodicityType?: PeriodicityType): string {
  const dow = periodicityType === 'INTERVAL' ? '' : `${isoDayOfWeekLabel(slot.dayOfWeek)} `;
  return `${dow}${formatAirportTime(slot.departureTime)}–${formatAirportTime(slot.arrivalTime)}`;
}

function scheduleSlotsCell(schedule: ScheduleRs): string {
  if (schedule.departureAtDate && schedule.arrivalAtDate) {
    return `${formatAirportDateTime(schedule.departureAtDate)} → ${formatAirportDateTime(schedule.arrivalAtDate)}`;
  }
  if (schedule.slots?.length) {
    return schedule.slots
      .map(s => formatSlotSummary(s, schedule.periodicityType))
      .join(', ');
  }
  return '—';
}

function schedulePeriodCell(schedule: ScheduleRs): string {
  const from = schedule.effectiveFrom ?? '—';
  const to = schedule.effectiveTo ?? '∞';
  return `${from} … ${to}`;
}

function scheduleActiveBadge(schedule: ScheduleRs): string {
  return schedule.isActive !== false ? 'Активен' : 'Неактивен';
}

function periodicityLabel(schedule: ScheduleRs): string {
  const step = schedule.periodicityStep ?? 1;
  if (schedule.periodicityType === 'INTERVAL') {
    return step === 1 ? 'ежедневно' : `каждые ${step} дн.`;
  }
  return step === 1 ? 'еженедельно' : `каждые ${step} нед.`;
}

export default function SchedulesPage() {
  const airportTz = getAirportTimezone();
  const [items, setItems] = useState<ScheduleRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [filterDate, setFilterDate] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [filterOrigin, setFilterOrigin] = useState('');
  const [filterDestination, setFilterDestination] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [reactivationHint, setReactivationHint] = useState<{
    scheduleId: number;
    flightNumber: string;
  } | null>(null);

  const load = useCallback(() => {
    setPageError(null);
    const params: Record<string, string> = {};
    if (filterDate) params.date = filterDate;
    if (filterAirline) params.airline = filterAirline;
    if (filterOrigin.length === 3) params.origin = filterOrigin;
    if (filterDestination.length === 3) params.destination = filterDestination;
    const request = debouncedSearch
      ? searchSchedules(debouncedSearch, params)
      : Object.keys(params).length > 0
        ? getFilteredSchedules(params)
        : getSchedules();
    request
      .then(setItems)
      .catch(e => setPageError(formatApiError(e)));
  }, [filterAirline, filterDate, filterDestination, filterOrigin, debouncedSearch]);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    getAirlines().then(setAirlines).catch(e => setPageError(formatApiError(e)));
  }, []);

  function clearFieldError(key: string) {
    setFieldErrors(prev => {
      if (!prev[key]) return prev;
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }

  function openCreateModal() {
    setModalError(null);
    setFieldErrors({});
    setForm({ ...EMPTY, effectiveFrom: todayAirportDate(), slots: [{ ...DEFAULT_SLOT }] });
    setEditingId(null);
    setCreateModalOpen(true);
  }

  function closeCreateModal() {
    setCreateModalOpen(false);
    setForm(EMPTY);
    setFieldErrors({});
    setModalError(null);
  }

  function openEditModal(schedule: ScheduleRs) {
    setModalError(null);
    setFieldErrors({});
    setEditingId(schedule.scheduleId);
    setForm(scheduleToForm(schedule));
    setEditModalOpen(true);
  }

  function closeEditModal() {
    setEditModalOpen(false);
    setEditingId(null);
    setForm(EMPTY);
    setFieldErrors({});
    setModalError(null);
  }

  function handlePeriodicityChange(type: PeriodicityType) {
    setForm(prev => ({
      ...prev,
      periodicityType: type,
      slots: type === 'INTERVAL'
        ? [{ dayOfWeek: '', departureTime: prev.slots[0]?.departureTime ?? '08:00', arrivalTime: prev.slots[0]?.arrivalTime ?? '10:00' }]
        : prev.slots.length
          ? prev.slots.map(s => ({ ...s, dayOfWeek: s.dayOfWeek || '1' }))
          : [{ ...DEFAULT_SLOT }],
    }));
    clearFieldError('slots');
  }

  function updateSlot(index: number, patch: Partial<ScheduleSlotFormValues>) {
    setForm(prev => ({
      ...prev,
      slots: prev.slots.map((s, i) => (i === index ? { ...s, ...patch } : s)),
    }));
    clearFieldError('slots');
  }

  function addSlot() {
    if (form.periodicityType === 'INTERVAL') return;
    setForm(prev => ({
      ...prev,
      slots: [...prev.slots, { dayOfWeek: '1', departureTime: '08:00', arrivalTime: '10:00' }],
    }));
    clearFieldError('slots');
  }

  function removeSlot(index: number) {
    setForm(prev => ({
      ...prev,
      slots: prev.slots.filter((_, i) => i !== index),
    }));
    clearFieldError('slots');
  }

  function runClientValidation(): boolean {
    const result = validateScheduleForm(form);
    if (!result.ok) {
      setFieldErrors(result.errors);
      setModalError('Исправьте ошибки в форме.');
      return false;
    }
    setFieldErrors({});
    return true;
  }

  function handleSubmitError(e: unknown) {
    const mapped = mapScheduleBackendError(e, form);
    if (mapped) {
      setFieldErrors(mapped.fieldErrors);
      setModalError(mapped.message);
    } else {
      setModalError(formatApiError(e));
    }
  }

  async function submitCreate() {
    if (!runClientValidation()) return;
    setSaving(true);
    setModalError(null);
    try {
      await createSchedule(buildSchedulePayload(form));
      closeCreateModal();
      load();
    } catch (e: unknown) {
      handleSubmitError(e);
    } finally {
      setSaving(false);
    }
  }

  async function submitEdit() {
    if (editingId == null) return;
    if (!runClientValidation()) return;
    setSaving(true);
    setModalError(null);
    try {
      const saved = await updateSchedule(editingId, buildSchedulePayload(form));
      closeEditModal();
      load();
      if (saved.reactivationSuggested) {
        setReactivationHint({
          scheduleId: saved.scheduleId,
          flightNumber: saved.flightNumber,
        });
      }
    } catch (e: unknown) {
      handleSubmitError(e);
    } finally {
      setSaving(false);
    }
  }

  async function activateScheduleTemplate(scheduleId: number) {
    const schedule = items.find(s => s.scheduleId === scheduleId);
    if (!schedule) return;
    try {
      await updateSchedule(scheduleId, buildSchedulePayload({
        ...scheduleToForm(schedule),
        isActive: true,
      }));
      setReactivationHint(null);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  async function remove(id: number) {
    if (!confirm('Удалить расписание?')) return;
    try {
      await deleteSchedule(id);
      if (editingId === id) closeEditModal();
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  return (
    <div className="page">
      <h1>Плановое расписание рейсов</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />

      {reactivationHint && (
        <div className="page-success-banner" role="status">
          Период действия шаблона {reactivationHint.flightNumber} продлён, но он выключен.
          {' '}
          <button
            type="button"
            className="btn-link"
            onClick={() => void activateScheduleTemplate(reactivationHint.scheduleId)}
          >
            Включить сейчас
          </button>
          <button
            type="button"
            className="btn-ghost btn-sm page-success-banner__close"
            onClick={() => setReactivationHint(null)}
            aria-label="Закрыть"
          >
            ✕
          </button>
        </div>
      )}

      <ScheduleFormModal
        open={createModalOpen}
        title="Добавить расписание"
        titleId="schedule-create-title"
        modalError={modalError}
        form={form}
        fieldErrors={fieldErrors}
        airlines={airlines}
        saving={saving}
        submitLabel="Создать"
        pendingLabel="Создание…"
        onClose={closeCreateModal}
        onSubmit={() => void submitCreate()}
        onFormChange={setForm}
        onClearFieldError={clearFieldError}
        onPeriodicityChange={handlePeriodicityChange}
        onUpdateSlot={updateSlot}
        onAddSlot={addSlot}
        onRemoveSlot={removeSlot}
      />

      <ScheduleFormModal
        open={editModalOpen && editingId != null}
        title="Редактирование расписания"
        titleId="schedule-edit-title"
        modalError={modalError}
        form={form}
        fieldErrors={fieldErrors}
        airlines={airlines}
        saving={saving}
        submitLabel="Сохранить"
        pendingLabel="Сохранение…"
        onClose={closeEditModal}
        onSubmit={() => void submitEdit()}
        onFormChange={setForm}
        onClearFieldError={clearFieldError}
        onPeriodicityChange={handlePeriodicityChange}
        onUpdateSlot={updateSlot}
        onAddSlot={addSlot}
        onRemoveSlot={removeSlot}
        dismissOnOverlayClick
      />

      <div className="form-row schedules-toolbar">
        <input
          placeholder="Поиск по номеру рейса…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <input
          type="date"
          value={filterDate}
          title={`День планового вылета (${airportTz})`}
          onChange={e => setFilterDate(e.target.value)}
        />
        <select value={filterAirline} onChange={e => setFilterAirline(e.target.value)}>
          <option value="">Все авиакомпании</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>{a.iataCode} — {a.name}</option>
          ))}
        </select>
        <input
          placeholder="Откуда (IATA)"
          maxLength={3}
          className="filter-iata-input"
          value={filterOrigin}
          onChange={e => setFilterOrigin(e.target.value.toUpperCase())}
        />
        <input
          placeholder="Куда (IATA)"
          maxLength={3}
          className="filter-iata-input"
          value={filterDestination}
          onChange={e => setFilterDestination(e.target.value.toUpperCase())}
        />
        <button type="button" className="btn-primary btn-sm" onClick={openCreateModal}>
          Добавить
        </button>
        <p className="filter-date-hint">
          Шаблон расписания → слот (день/время) → рейс на дату (статус SCHEDULED/DEPARTED/… только у рейса).
          {' '}
          Экспорт операционного расписания на день (PDF/Excel) — на странице «Рейсы».
          {' '}
          {filterDate
            ? `Показаны шаблоны с рейсом на ${filterDate} (MSK).`
            : 'Фильтр по дате не задан — все шаблоны.'}
        </p>
      </div>

      <table className="data-table">
        <thead>
          <tr>
            <th>Рейс</th><th>Откуда</th><th>Куда</th>
            <th>Слоты</th><th>Период</th><th>Периодичность</th><th>Статус</th><th>Авиакомпания</th><th></th>
          </tr>
        </thead>
        <tbody>
          {items.map(s => (
            <tr key={s.scheduleId}>
              <td>{s.flightNumber}</td>
              <td>{s.originAirport.trim()}</td>
              <td>{s.destinationAirport.trim()}</td>
              <td>{scheduleSlotsCell(s)}</td>
              <td>{schedulePeriodCell(s)}</td>
              <td>{periodicityLabel(s)}</td>
              <td>
                <span className={s.isActive !== false ? 'badge badge--active' : 'badge badge--inactive'}>
                  {scheduleActiveBadge(s)}
                </span>
              </td>
              <td>{s.airline?.name ?? '—'}</td>
              <td className="cell-actions">
                <button type="button" className="btn-ghost btn-sm" onClick={() => openEditModal(s)}>✏</button>
                <button type="button" className="btn-danger btn-sm" onClick={() => void remove(s.scheduleId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
