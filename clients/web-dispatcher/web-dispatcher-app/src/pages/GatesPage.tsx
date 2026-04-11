import { useEffect, useState } from 'react';
import { createGate, getGates, updateGate } from '../services/api';
import type { GateRs } from '../types';

const EMPTY = { gateNumber: '', terminal: '', isActive: true, maxSizeCategory: 'NARROW' };
function apiErr(e: unknown) { return e instanceof Error ? e.message : String(e); }

export default function GatesPage() {
  const [items, setItems] = useState<GateRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => getGates().then(setItems).catch(e => setError(apiErr(e)));
  useEffect(() => { load(); }, []);

  async function save() {
    setError(null);
    const body = { ...form, isActive: Boolean(form.isActive) };
    try {
      if (editing != null) await updateGate(editing, body);
      else await createGate(body);
      setForm(EMPTY); setEditing(null); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  function startEdit(g: GateRs) {
    setEditing(g.gateId);
    setForm({
      gateNumber: g.gateNumber,
      terminal: g.terminal ?? '',
      isActive: g.isActive,
      maxSizeCategory: g.maxSizeCategory ?? 'NARROW',
    });
  }

  return (
    <div className="page">
      <h1>Гейты</h1>
      {error && <p className="page-error">{error}</p>}
      <div className="form-row">
        <input placeholder="Номер гейта" value={form.gateNumber}
          onChange={e => setForm(f => ({ ...f, gateNumber: e.target.value }))} />
        <input placeholder="Терминал" value={form.terminal}
          onChange={e => setForm(f => ({ ...f, terminal: e.target.value }))} />
        <select value={form.maxSizeCategory}
          onChange={e => setForm(f => ({ ...f, maxSizeCategory: e.target.value }))}>
          <option value="NARROW">NARROW</option>
          <option value="WIDE">WIDE</option>
          <option value="JUMBO">JUMBO</option>
        </select>
        <label className="inline-check">
          <input type="checkbox" checked={form.isActive}
            onChange={e => setForm(f => ({ ...f, isActive: e.target.checked }))} />
          Активен
        </label>
        <button className="btn-primary btn-sm" onClick={save}>
          {editing != null ? 'Сохранить' : 'Добавить'}
        </button>
        {editing != null && (
          <button className="btn-ghost btn-sm" onClick={() => { setEditing(null); setForm(EMPTY); }}>
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
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
