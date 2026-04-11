import { useEffect, useState } from 'react';
import { createAircraftType, deleteAircraftType, getAircraftTypes, updateAircraftType } from '../services/api';
import type { AircraftTypeRs } from '../types';

const EMPTY = { icaoCode: '', passengerCapacity: '', sizeCategory: 'NARROW' };

function apiErr(e: unknown) { return e instanceof Error ? e.message : String(e); }

export default function AircraftTypesPage() {
  const [items, setItems] = useState<AircraftTypeRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => getAircraftTypes().then(setItems).catch(e => setError(apiErr(e)));
  useEffect(() => { load(); }, []);

  async function save() {
    setError(null);
    const body = {
      icaoCode: form.icaoCode,
      passengerCapacity: form.passengerCapacity ? Number(form.passengerCapacity) : undefined,
      sizeCategory: form.sizeCategory,
    };
    try {
      if (editing != null) await updateAircraftType(editing, body);
      else await createAircraftType(body);
      setForm(EMPTY); setEditing(null); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function remove(id: number) {
    if (!confirm('Удалить тип ВС?')) return;
    try { await deleteAircraftType(id); load(); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  function startEdit(a: AircraftTypeRs) {
    setEditing(a.aircraftTypeId);
    setForm({
      icaoCode: a.icaoCode,
      passengerCapacity: String(a.passengerCapacity ?? ''),
      sizeCategory: a.sizeCategory ?? 'NARROW',
    });
  }

  return (
    <div className="page">
      <h1>Типы воздушных судов</h1>
      {error && <p className="page-error">{error}</p>}
      <div className="form-row">
        <input placeholder="ICAO (4 буквы)" maxLength={4} value={form.icaoCode}
          onChange={e => setForm(f => ({ ...f, icaoCode: e.target.value.toUpperCase() }))} />
        <input placeholder="Вместимость" type="number" min={0} value={form.passengerCapacity}
          onChange={e => setForm(f => ({ ...f, passengerCapacity: e.target.value }))} />
        <select value={form.sizeCategory}
          onChange={e => setForm(f => ({ ...f, sizeCategory: e.target.value }))}>
          <option value="NARROW">NARROW</option>
          <option value="WIDE">WIDE</option>
          <option value="JUMBO">JUMBO</option>
        </select>
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
        <thead><tr><th>ICAO</th><th>Вместимость</th><th>Категория</th><th></th></tr></thead>
        <tbody>
          {items.map(a => (
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
