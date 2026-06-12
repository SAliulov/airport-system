import { useCallback, useEffect, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { createAircraftType, deleteAircraftType, getAircraftTypes, updateAircraftType } from '../services/api';
import type { AircraftTypeRs } from '../types';
import { formatApiError } from '../utils/apiError';
import { validateAircraftTypeForm } from '../utils/fieldValidation';

const EMPTY = { icaoCode: '', passengerCapacity: '', sizeCategory: 'NARROW' };

export default function AircraftTypesPage() {
  const [items, setItems] = useState<AircraftTypeRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [filter, setFilter] = useState('');
  const [pageError, setPageError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const load = useCallback(() => {
    setPageError(null);
    getAircraftTypes()
      .then(setItems)
      .catch(e => setPageError(formatApiError(e)));
  }, []);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  async function save() {
    setPageError(null);
    const validation = validateAircraftTypeForm(form);
    if (!validation.ok) {
      setFieldErrors(validation.errors);
      return;
    }
    setFieldErrors({});
    const body = {
      icaoCode: form.icaoCode.trim().toUpperCase(),
      passengerCapacity: form.passengerCapacity ? Number(form.passengerCapacity) : undefined,
      sizeCategory: form.sizeCategory,
    };
    try {
      if (editing != null) await updateAircraftType(editing, body);
      else await createAircraftType(body);
      setForm(EMPTY);
      setEditing(null);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  async function remove(id: number) {
    if (!confirm('Удалить тип ВС?')) return;
    setPageError(null);
    try {
      await deleteAircraftType(id);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  function startEdit(a: AircraftTypeRs) {
    setEditing(a.aircraftTypeId);
    setFieldErrors({});
    setForm({
      icaoCode: a.icaoCode,
      passengerCapacity: String(a.passengerCapacity ?? ''),
      sizeCategory: a.sizeCategory ?? 'NARROW',
    });
  }

  function clearForm() {
    setEditing(null);
    setForm(EMPTY);
    setFieldErrors({});
  }

  const visibleItems = items.filter(a => {
    const q = filter.trim().toLowerCase();
    if (!q) return true;
    return [
      a.icaoCode,
      String(a.passengerCapacity ?? ''),
      a.sizeCategory ?? '',
    ].some(value => value.toLowerCase().includes(q));
  });

  return (
    <div className="page">
      <h1>Типы воздушных судов</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />
      <div className="form-row form-row--toolbar directory-filter-row">
        <input
          placeholder="Фильтр по ICAO, вместимости или категории"
          value={filter}
          onChange={e => setFilter(e.target.value)}
        />
        {filter && (
          <button type="button" className="btn-ghost btn-sm" onClick={() => setFilter('')}>
            Сбросить
          </button>
        )}
      </div>
      <div className="form-row directory-form-row">
        <div className="inline-field">
          <input
            placeholder="ICAO (2–4 символа)"
            maxLength={4}
            value={form.icaoCode}
            className={fieldErrors.icaoCode ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, icaoCode: e.target.value.toUpperCase() }))}
          />
          {fieldErrors.icaoCode && <span className="modal-field__error">{fieldErrors.icaoCode}</span>}
        </div>
        <div className="inline-field">
          <input
            placeholder="Вместимость"
            type="number"
            min={0}
            max={999}
            value={form.passengerCapacity}
            className={fieldErrors.passengerCapacity ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, passengerCapacity: e.target.value.slice(0, 3) }))}
          />
          {fieldErrors.passengerCapacity && (
            <span className="modal-field__error">{fieldErrors.passengerCapacity}</span>
          )}
        </div>
        <div className="inline-field">
          <select
            value={form.sizeCategory}
            className={fieldErrors.sizeCategory ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, sizeCategory: e.target.value }))}
          >
            <option value="NARROW">NARROW</option>
            <option value="WIDE">WIDE</option>
            <option value="JUMBO">JUMBO</option>
          </select>
          {fieldErrors.sizeCategory && <span className="modal-field__error">{fieldErrors.sizeCategory}</span>}
        </div>
        <button className="btn-primary btn-sm" onClick={save}>
          {editing != null ? 'Сохранить' : 'Добавить'}
        </button>
        {editing != null && (
          <button className="btn-ghost btn-sm" onClick={clearForm}>
            Отмена
          </button>
        )}
      </div>
      <table className="data-table">
        <thead><tr><th>ICAO</th><th>Вместимость</th><th>Категория</th><th></th></tr></thead>
        <tbody>
          {visibleItems.map(a => (
            <tr key={a.aircraftTypeId}>
              <td>{a.icaoCode}</td>
              <td>{a.passengerCapacity ?? '—'}</td>
              <td>{a.sizeCategory ?? '—'}</td>
              <td className="cell-actions">
                <button className="btn-ghost btn-sm" onClick={() => startEdit(a)}>✏</button>
                <button className="btn-danger btn-sm" onClick={() => remove(a.aircraftTypeId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
