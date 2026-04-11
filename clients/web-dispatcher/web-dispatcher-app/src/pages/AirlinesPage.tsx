import { useEffect, useState } from 'react';
import { createAirline, deleteAirline, getAirlines, updateAirline } from '../services/api';
import type { AirlineRs } from '../types';

const EMPTY = { iataCode: '', name: '', country: '' };

export default function AirlinesPage() {
  const [items, setItems] = useState<AirlineRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => getAirlines().then(setItems).catch(e => setError(String(e)));

  useEffect(() => { load(); }, []);

  async function save() {
    setError(null);
    try {
      if (editing != null) await updateAirline(editing, form);
      else await createAirline(form);
      setForm(EMPTY); setEditing(null); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function remove(id: number) {
    if (!confirm('Удалить авиакомпанию?')) return;
    setError(null);
    try { await deleteAirline(id); load(); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  function startEdit(a: AirlineRs) {
    setEditing(a.airlineId);
    setForm({ iataCode: a.iataCode, name: a.name, country: a.country ?? '' });
  }

  return (
    <div className="page">
      <h1>Авиакомпании</h1>
      {error && <p className="page-error">{error}</p>}
      <div className="form-row">
        <input placeholder="IATA (2 буквы)" maxLength={2} value={form.iataCode}
          onChange={e => setForm(f => ({ ...f, iataCode: e.target.value.toUpperCase() }))} />
        <input placeholder="Название" value={form.name}
          onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
        <input placeholder="Страна" value={form.country}
          onChange={e => setForm(f => ({ ...f, country: e.target.value }))} />
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

function apiErr(e: unknown) {
  if (e instanceof Error) return e.message;
  return String(e);
}
