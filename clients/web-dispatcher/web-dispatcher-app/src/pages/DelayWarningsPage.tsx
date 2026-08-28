import { useCallback, useEffect, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { ConfirmDialog } from '../shared/components/ConfirmDialog';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { deleteDelayWarning, getAirlines, getDelayWarnings, updateDelayWarning } from '../services/api';
import type { AirlineRs, DelayWarningRs } from '../types';
import { formatApiError } from '../utils/apiError';
import { formatAirportDateTime, getAirportTimezone } from '../utils/airportTime';

const EMPTY_EDIT = { delayMinutes: '', reason: '' };

/** Сводный список предупреждений о задержках по всем рейсам: фильтры, редактирование, отмена. */
export default function DelayWarningsPage() {
  const airportTz = getAirportTimezone();
  const [items, setItems] = useState<DelayWarningRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [filterDate, setFilterDate] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [search, setSearch] = useState('');
  const debouncedSearch = useDebouncedValue(search.trim(), 400);
  const [pageError, setPageError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<number | null>(null);
  const [editForm, setEditForm] = useState(EMPTY_EDIT);
  const [modalError, setModalError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [deleteSaving, setDeleteSaving] = useState(false);

  const load = useCallback(() => {
    setPageError(null);
    getDelayWarnings({
      date: filterDate || undefined,
      airline: filterAirline ? Number(filterAirline) : undefined,
      query: debouncedSearch || undefined,
    })
      .then(setItems)
      .catch(e => setPageError(formatApiError(e)));
  }, [filterDate, filterAirline, debouncedSearch]);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    getAirlines().then(setAirlines).catch(e => setPageError(formatApiError(e)));
  }, []);

  function startEdit(w: DelayWarningRs) {
    setEditingId(w.warningId);
    setEditForm({ delayMinutes: String(w.delayMinutes), reason: w.reason ?? '' });
    setModalError(null);
  }

  function clearEdit() {
    setEditingId(null);
    setEditForm(EMPTY_EDIT);
    setModalError(null);
  }

  async function saveEdit() {
    if (editingId == null) return;
    const minutes = Number(editForm.delayMinutes);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      setModalError('Минуты задержки должны быть положительным числом.');
      return;
    }
    setSaving(true);
    setModalError(null);
    try {
      await updateDelayWarning(editingId, { delayMinutes: minutes, reason: editForm.reason.trim() || undefined });
      clearEdit();
      load();
    } catch (e: unknown) {
      setModalError(formatApiError(e));
    } finally {
      setSaving(false);
    }
  }

  async function confirmRemove() {
    if (pendingDeleteId == null) return;
    const id = pendingDeleteId;
    setDeleteSaving(true);
    try {
      await deleteDelayWarning(id);
      if (editingId === id) clearEdit();
      load();
      setPendingDeleteId(null);
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    } finally {
      setDeleteSaving(false);
    }
  }

  const editingWarning = items.find(w => w.warningId === editingId) ?? null;

  return (
    <div className="page">
      <h1>Задержки</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />

      <div className="form-row form-row--toolbar">
        <input
          type="date"
          value={filterDate}
          title={`День планового вылета (${airportTz})`}
          onChange={e => setFilterDate(e.target.value)}
        />
        <button type="button" className="btn-ghost btn-sm" onClick={() => setFilterDate('')}>
          Все даты
        </button>
        <select value={filterAirline} onChange={e => setFilterAirline(e.target.value)}>
          <option value="">Все авиакомпании</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>{a.iataCode} — {a.name}</option>
          ))}
        </select>
        <input
          placeholder="Поиск по номеру рейса…"
          value={search}
          onChange={e => setSearch(e.target.value.toUpperCase())}
        />
        <p className="filter-date-hint">
          {filterDate
            ? `Показаны предупреждения по рейсам с плановым вылетом ${filterDate} (${airportTz}).`
            : 'Фильтр по дате не задан — все предупреждения.'}
        </p>
      </div>

      {editingId != null && (
        <div className="form-row directory-form-row">
          <span>
            Рейс {editingWarning?.flightNumber ?? `#${editingWarning?.flightId ?? editingId}`}
          </span>
          <div className="inline-field">
            <input
              type="number"
              min={1}
              placeholder="Минуты задержки"
              value={editForm.delayMinutes}
              onChange={e => setEditForm(f => ({ ...f, delayMinutes: e.target.value }))}
            />
          </div>
          <div className="inline-field">
            <input
              placeholder="Причина"
              maxLength={500}
              value={editForm.reason}
              onChange={e => setEditForm(f => ({ ...f, reason: e.target.value }))}
            />
          </div>
          <button type="button" className="btn-primary btn-sm" disabled={saving} onClick={() => void saveEdit()}>
            {saving ? 'Сохранение…' : 'Сохранить'}
          </button>
          <button type="button" className="btn-ghost btn-sm" onClick={clearEdit}>
            Отмена
          </button>
          {modalError && <span className="modal-field__error">{modalError}</span>}
        </div>
      )}

      <table className="data-table">
        <thead>
          <tr>
            <th>Рейс</th><th>Маршрут</th><th>Плановый вылет</th>
            <th>Минуты</th><th>Причина</th><th>Создано</th><th></th>
          </tr>
        </thead>
        <tbody>
          {items.map(w => (
            <tr key={w.warningId}>
              <td>{w.flightNumber ?? `#${w.flightId ?? '—'}`}</td>
              <td>{w.originAirport && w.destinationAirport ? `${w.originAirport} → ${w.destinationAirport}` : '—'}</td>
              <td>{w.scheduledDeparture ? formatAirportDateTime(w.scheduledDeparture) : '—'}</td>
              <td>{w.delayMinutes}</td>
              <td>{w.reason ?? '—'}</td>
              <td>{w.createdAt ? formatAirportDateTime(w.createdAt) : '—'}</td>
              <td className="cell-actions">
                <button type="button" className="btn-ghost btn-sm" onClick={() => startEdit(w)}>✏</button>
                <button type="button" className="btn-danger btn-sm" onClick={() => setPendingDeleteId(w.warningId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ConfirmDialog
        open={pendingDeleteId != null}
        title="Отменить предупреждение о задержке?"
        message="Действие необратимо."
        variant="danger"
        busy={deleteSaving}
        onCancel={() => setPendingDeleteId(null)}
        onConfirm={() => void confirmRemove()}
      />
    </div>
  );
}
