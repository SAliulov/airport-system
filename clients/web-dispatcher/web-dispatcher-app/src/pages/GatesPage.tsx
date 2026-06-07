import { useCallback, useEffect, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { createGate, deleteGate, getGates, updateGate } from '../services/api';
import type { GateRs } from '../types';
import { formatApiError } from '../utils/apiError';
import { validateGateForm } from '../utils/fieldValidation';

const EMPTY = { gateNumber: '', terminal: '', isActive: true, maxSizeCategory: 'NARROW' };

export default function GatesPage() {
  const [items, setItems] = useState<GateRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [pageError, setPageError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const load = useCallback(() => {
    setPageError(null);
    getGates()
      .then(setItems)
      .catch(e => setPageError(formatApiError(e)));
  }, []);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  async function save() {
    setPageError(null);
    const validation = validateGateForm({
      gateNumber: form.gateNumber,
      terminal: form.terminal,
      maxSizeCategory: form.maxSizeCategory,
    });
    if (!validation.ok) {
      setFieldErrors(validation.errors);
      return;
    }
    setFieldErrors({});
    const body = { ...form, isActive: Boolean(form.isActive) };
    try {
      if (editing != null) await updateGate(editing, body);
      else await createGate(body);
      setForm(EMPTY);
      setEditing(null);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  async function remove(id: number) {
    if (!confirm('Удалить гейт?')) return;
    setPageError(null);
    try {
      await deleteGate(id);
      load();
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    }
  }

  function startEdit(g: GateRs) {
    setEditing(g.gateId);
    setFieldErrors({});
    setForm({
      gateNumber: g.gateNumber,
      terminal: g.terminal ?? '',
      isActive: g.isActive,
      maxSizeCategory: g.maxSizeCategory ?? 'NARROW',
    });
  }

  function clearForm() {
    setEditing(null);
    setForm(EMPTY);
    setFieldErrors({});
  }

  return (
    <div className="page">
      <h1>Гейты</h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />
      <div className="form-row">
        <div className="inline-field">
          <input
            placeholder="Номер гейта"
            maxLength={10}
            value={form.gateNumber}
            className={fieldErrors.gateNumber ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, gateNumber: e.target.value }))}
          />
          {fieldErrors.gateNumber && <span className="modal-field__error">{fieldErrors.gateNumber}</span>}
        </div>
        <div className="inline-field">
          <input
            placeholder="Терминал"
            maxLength={10}
            value={form.terminal}
            className={fieldErrors.terminal ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, terminal: e.target.value }))}
          />
          {fieldErrors.terminal && <span className="modal-field__error">{fieldErrors.terminal}</span>}
        </div>
        <div className="inline-field">
          <select
            value={form.maxSizeCategory}
            className={fieldErrors.maxSizeCategory ? 'field-invalid' : undefined}
            onChange={e => setForm(f => ({ ...f, maxSizeCategory: e.target.value }))}
          >
            <option value="NARROW">NARROW</option>
            <option value="WIDE">WIDE</option>
            <option value="JUMBO">JUMBO</option>
          </select>
          {fieldErrors.maxSizeCategory && (
            <span className="modal-field__error">{fieldErrors.maxSizeCategory}</span>
          )}
        </div>
        <label className="modal-field__checkbox inline-check">
          <input
            type="checkbox"
            checked={form.isActive}
            onChange={e => setForm(f => ({ ...f, isActive: e.target.checked }))}
          />
          <span className="modal-field__checkbox-text">Активен</span>
        </label>
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
        <thead><tr><th>Номер</th><th>Терминал</th><th>Макс. категория</th><th>Активен</th><th></th></tr></thead>
        <tbody>
          {items.map(g => (
            <tr key={g.gateId}>
              <td>{g.gateNumber}</td>
              <td>{g.terminal ?? '—'}</td>
              <td>{g.maxSizeCategory ?? '—'}</td>
              <td>{g.isActive ? '✓' : '—'}</td>
              <td className="cell-actions">
                <button className="btn-ghost btn-sm" onClick={() => startEdit(g)}>✏</button>
                <button className="btn-danger btn-sm" onClick={() => remove(g.gateId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
