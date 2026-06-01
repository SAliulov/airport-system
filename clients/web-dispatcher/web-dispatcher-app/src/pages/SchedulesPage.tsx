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
  fromTimeInputValue,
  isoDayOfWeekLabel,
  todayAirportDate,
  toTimeInputValue,
} from '../utils/airportTime';
import {
  mapScheduleBackendError,
  validateScheduleForm,
  type ScheduleFormValues,
  type ScheduleSlotFormValues,
} from '../utils/fieldValidation';

const STATUSES = ['SCHEDULED', 'DEPARTED', 'ARRIVED', 'DELAYED', 'CANCELLED'];

const DOW_OPTIONS = [
  { value: '1', label: 'Пн' },
  { value: '2', label: 'Вт' },
  { value: '3', label: 'Ср' },
  { value: '4', label: 'Чт' },
  { value: '5', label: 'Пт' },
  { value: '6', label: 'Сб' },
  { value: '7', label: 'Вс' },
];

const DEFAULT_SLOT: ScheduleSlotFormValues = {
  dayOfWeek: '1',
  departureTime: '08:00',
  arrivalTime: '10:00',
};

const EMPTY: ScheduleFormValues = {
  flightNumber: '',
  originAirport: '',
  destinationAirport: '',
  effectiveFrom: todayAirportDate(),
  effectiveTo: '',
  isActive: true,
  periodicityType: 'WEEKLY',
  periodicityStep: '1',
  airlineId: '',
  slots: [{ ...DEFAULT_SLOT }],
};

function scheduleToForm(schedule: ScheduleRs): ScheduleFormValues {
  const slots = schedule.slots?.length
    ? schedule.slots.map(s => ({
        slotId: s.slotId != null ? String(s.slotId) : undefined,
        dayOfWeek: s.dayOfWeek != null ? String(s.dayOfWeek) : '',
        departureTime: toTimeInputValue(s.departureTime),
        arrivalTime: toTimeInputValue(s.arrivalTime),
      }))
    : [{ ...DEFAULT_SLOT }];

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

function buildSchedulePayload(form: ScheduleFormValues) {
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
      departureTime: fromTimeInputValue(slot.departureTime),
      arrivalTime: fromTimeInputValue(slot.arrivalTime),
    })),
  };
}

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
  const [items, setItems] = useState<ScheduleRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [filterDate, setFilterDate] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterOrigin, setFilterOrigin] = useState('');
  const [filterDestination, setFilterDestination] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setPageError(null);
    const params: Record<string, string> = {};
    if (filterDate) params.date = filterDate;
    if (filterAirline) params.airline = filterAirline;
    if (filterStatus) params.status = filterStatus;
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
  }, [filterAirline, filterDate, filterDestination, filterOrigin, filterStatus, debouncedSearch]);

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
      await updateSchedule(editingId, buildSchedulePayload(form));
      closeEditModal();
      load();
    } catch (e: unknown) {
      handleSubmitError(e);
    } finally {
      setSaving(false);
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

  function invalidClass(field: string) {
    return fieldErrors[field] ? 'field-invalid' : '';
  }

  function scheduleFormFields() {
    return (
      <div className="modal-form-grid">
        <div className="modal-field">
          <span className="modal-field__label">Номер рейса</span>
          <input
            className={invalidClass('flightNumber')}
            placeholder="SU1234"
            maxLength={20}
            value={form.flightNumber}
            onChange={e => {
              clearFieldError('flightNumber');
              setForm(p => ({ ...p, flightNumber: e.target.value.toUpperCase() }));
            }}
          />
          {fieldErrors.flightNumber && (
            <span className="modal-field__error">{fieldErrors.flightNumber}</span>
          )}
        </div>

        <div className="modal-form-row">
          <div className="modal-field">
            <span className="modal-field__label">Откуда (IATA)</span>
            <input
              className={invalidClass('originAirport')}
              maxLength={3}
              placeholder="SVO"
              value={form.originAirport}
              onChange={e => {
                clearFieldError('originAirport');
                clearFieldError('destinationAirport');
                setForm(p => ({ ...p, originAirport: e.target.value.toUpperCase() }));
              }}
            />
            {fieldErrors.originAirport && (
              <span className="modal-field__error">{fieldErrors.originAirport}</span>
            )}
          </div>
          <div className="modal-field">
            <span className="modal-field__label">Куда (IATA)</span>
            <input
              className={invalidClass('destinationAirport')}
              maxLength={3}
              placeholder="LED"
              value={form.destinationAirport}
              onChange={e => {
                clearFieldError('destinationAirport');
                clearFieldError('originAirport');
                setForm(p => ({ ...p, destinationAirport: e.target.value.toUpperCase() }));
              }}
            />
            {fieldErrors.destinationAirport && (
              <span className="modal-field__error">{fieldErrors.destinationAirport}</span>
            )}
          </div>
        </div>

        <div className="modal-form-row">
          <div className="modal-field">
            <span className="modal-field__label">Действует с</span>
            <input
              className={invalidClass('effectiveFrom')}
              type="date"
              value={form.effectiveFrom}
              onChange={e => {
                clearFieldError('effectiveFrom');
                clearFieldError('effectiveTo');
                setForm(p => ({ ...p, effectiveFrom: e.target.value }));
              }}
            />
            {fieldErrors.effectiveFrom && (
              <span className="modal-field__error">{fieldErrors.effectiveFrom}</span>
            )}
          </div>
          <div className="modal-field">
            <span className="modal-field__label">Действует по</span>
            <input
              className={invalidClass('effectiveTo')}
              type="date"
              value={form.effectiveTo}
              onChange={e => {
                clearFieldError('effectiveTo');
                setForm(p => ({ ...p, effectiveTo: e.target.value }));
              }}
            />
            {fieldErrors.effectiveTo && (
              <span className="modal-field__error">{fieldErrors.effectiveTo}</span>
            )}
          </div>
        </div>

        <div className="modal-form-row">
          <div className="modal-field">
            <span className="modal-field__label">Периодичность</span>
            <select
              className={invalidClass('periodicityType')}
              value={form.periodicityType}
              onChange={e => handlePeriodicityChange(e.target.value as PeriodicityType)}
            >
              <option value="WEEKLY">WEEKLY — по дням недели</option>
              <option value="INTERVAL">INTERVAL — через N дней</option>
            </select>
          </div>
          <div className="modal-field">
            <span className="modal-field__label">Шаг</span>
            <input
              className={invalidClass('periodicityStep')}
              type="number"
              min={1}
              value={form.periodicityStep}
              onChange={e => {
                clearFieldError('periodicityStep');
                setForm(p => ({ ...p, periodicityStep: e.target.value }));
              }}
            />
            {fieldErrors.periodicityStep && (
              <span className="modal-field__error">{fieldErrors.periodicityStep}</span>
            )}
          </div>
        </div>

        <div className="modal-field">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <input
              type="checkbox"
              checked={form.isActive}
              onChange={e => setForm(p => ({ ...p, isActive: e.target.checked }))}
            />
            Активный шаблон
          </label>
          <p style={{ fontSize: 12, color: 'var(--muted)', marginTop: 6, marginLeft: 28 }}>
            Нельзя снять активность, если по шаблону уже созданы рейсы — сначала удалите рейсы.
          </p>
        </div>

        <div className="modal-field">
          <span className="modal-field__label">Слоты</span>
          <p style={{ fontSize: 12, color: 'var(--muted)', marginBottom: 8 }}>
            День недели слота должен попадать в период действия шаблона — иначе при сохранении будет ошибка.
          </p>
          {form.slots.map((slot, index) => (
            <div key={index} className="modal-form-row" style={{ marginBottom: 8 }}>
              {form.periodicityType === 'WEEKLY' && (
                <select
                  className={invalidClass('slots')}
                  value={slot.dayOfWeek}
                  onChange={e => updateSlot(index, { dayOfWeek: e.target.value })}
                >
                  {DOW_OPTIONS.map(o => (
                    <option key={o.value} value={o.value}>{o.label}</option>
                  ))}
                </select>
              )}
              <input
                className={invalidClass('slots')}
                type="time"
                value={slot.departureTime}
                title="Вылет"
                onChange={e => updateSlot(index, { departureTime: e.target.value })}
              />
              <input
                className={invalidClass('slots')}
                type="time"
                value={slot.arrivalTime}
                title="Прилёт"
                onChange={e => updateSlot(index, { arrivalTime: e.target.value })}
              />
              {form.periodicityType === 'WEEKLY' && form.slots.length > 1 && (
                <button type="button" className="btn-ghost btn-sm" onClick={() => removeSlot(index)}>
                  ✕
                </button>
              )}
            </div>
          ))}
          {form.periodicityType === 'WEEKLY' && (
            <button type="button" className="btn-ghost btn-sm" onClick={addSlot}>
              + слот
            </button>
          )}
          {fieldErrors.slots && (
            <span className="modal-field__error">{fieldErrors.slots}</span>
          )}
        </div>

        <div className="modal-field">
          <span className="modal-field__label">Авиакомпания</span>
          <select
            className={invalidClass('airlineId')}
            value={form.airlineId}
            onChange={e => {
              clearFieldError('airlineId');
              setForm(p => ({ ...p, airlineId: e.target.value }));
            }}
          >
            <option value="">— выберите —</option>
            {airlines.map(a => (
              <option key={a.airlineId} value={String(a.airlineId)}>
                {a.iataCode} — {a.name}
              </option>
            ))}
          </select>
          {fieldErrors.airlineId && (
            <span className="modal-field__error">{fieldErrors.airlineId}</span>
          )}
        </div>
      </div>
    );
  }

  const modalFooter = (onCancel: () => void, onSubmit: () => void, submitLabel: string, pendingLabel: string) => (
    <div className="modal-actions modal-actions--center">
      <button type="button" className="btn-ghost" onClick={onCancel} disabled={saving}>
        Отмена
      </button>
      <button type="button" className="btn-primary" disabled={saving} onClick={onSubmit}>
        {saving ? pendingLabel : submitLabel}
      </button>
    </div>
  );

  return (
    <div className="page">
      <h1>Плановое расписание рейсов</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />

      {createModalOpen && (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="schedule-create-title">
          <div className="modal-card modal-card--form">
            <h3 id="schedule-create-title">Добавить расписание</h3>
            {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
            {scheduleFormFields()}
            {modalFooter(
              closeCreateModal,
              () => void submitCreate(),
              'Создать',
              'Создание…',
            )}
          </div>
        </div>
      )}

      {editModalOpen && editingId != null && (
        <div
          className="modal-overlay"
          role="dialog"
          aria-modal="true"
          aria-labelledby="schedule-edit-title"
          onClick={e => {
            if (e.target === e.currentTarget) closeEditModal();
          }}
        >
          <div className="modal-card modal-card--form" onClick={e => e.stopPropagation()}>
            <h3 id="schedule-edit-title">Редактирование расписания</h3>
            {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
            {scheduleFormFields()}
            {modalFooter(
              closeEditModal,
              () => void submitEdit(),
              'Сохранить',
              'Сохранение…',
            )}
          </div>
        </div>
      )}

      <div className="form-row" style={{ marginBottom: 8, flexWrap: 'wrap', gap: 8 }}>
        <input
          placeholder="Поиск по номеру рейса…"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <input
          type="date"
          value={filterDate}
          title="День планового вылета (Europe/Moscow)"
          onChange={e => setFilterDate(e.target.value)}
        />
        <select value={filterAirline} onChange={e => setFilterAirline(e.target.value)}>
          <option value="">Все авиакомпании</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>{a.iataCode} — {a.name}</option>
          ))}
        </select>
        <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
          <option value="">Все статусы</option>
          {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
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
        <button type="button" className="btn-primary btn-sm" onClick={openCreateModal}>
          Добавить
        </button>
        <p className="filter-date-hint">
          {filterDate
            ? `Показаны слоты с плановым вылетом ${filterDate} (MSK).`
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
