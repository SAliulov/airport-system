import { useCallback, useEffect, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { createAirline, deleteAirline, getAirlines, updateAirline } from '../services/api';
import type { AirlineRs } from '../types';
import { formatApiError } from '../utils/apiError';
import { validateAirlineForm } from '../utils/fieldValidation';

const EMPTY = { iataCode: '', name: '', country: '' };

export default function AirlinesPage() {
  const [items, setItems] = useState<AirlineRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const load = useCallback(() => {
    setPageError(null);
    getAirlines()
      .then(setItems)
      .catch(e => setPageError(formatApiError(e)));
  }, []);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  async function save() {
    setPageError(null);
    const validation = validateAirlineForm(form);
    if (!validation.ok) {
      setFieldErrors(validation.errors);
      return;
    }
    setFieldErrors({});
    try {
      if (editing != null) await updateAirline(editing, form);
      else await createAirline(form);
      setForm(EMPTY);
      setEditing(null);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  async function remove(id: number) {
    if (!confirm('Удалить авиакомпанию?')) return;
    setPageError(null);
    try {
      await deleteAirline(id);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  function startEdit(a: AirlineRs) {
    setEditing(a.airlineId);
    setFieldErrors({});
    setForm({ iataCode: a.iataCode, name: a.name, country: a.country ?? '' });
  }

  function clearForm() {
    setEditing(null);
    setForm(EMPTY);
    setFieldErrors({});
  }

  return (
    <div className="page">
      <h1>Авиакомпании</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />
      <div className="form-row">
        <div className="inline-field">
          <input
            placeholder="IATA (2 символа)"
            maxLength={2}
            value={form.iataCode}
            className={fieldErrors.iataCode ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, iataCode: e.target.value.toUpperCase() }))}
          />
          {fieldErrors.iataCode && <span className="modal-field__error">{fieldErrors.iataCode}</span>}
        </div>
        <div className="inline-field">
          <input
            placeholder="Название"
            maxLength={100}
            value={form.name}
            className={fieldErrors.name ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
          />
          {fieldErrors.name && <span className="modal-field__error">{fieldErrors.name}</span>}
        </div>
        <div className="inline-field">
          <input
            placeholder="Страна"
            maxLength={77}
            value={form.country}
            className={fieldErrors.country ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, country: e.target.value }))}
          />
          {fieldErrors.country && <span className="modal-field__error">{fieldErrors.country}</span>}
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
        <thead><tr><th>IATA</th><th>Название</th><th>Страна</th><th></th></tr></thead>
        <tbody>
          {items.map(a => (
            <tr key={a.airlineId}>
              <td>{a.iataCode}</td><td>{a.name}</td><td>{a.country ?? '—'}</td>
              <td className="cell-actions">
                <button className="btn-ghost btn-sm" onClick={() => startEdit(a)}>✏</button>
                <button className="btn-danger btn-sm" onClick={() => remove(a.airlineId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
