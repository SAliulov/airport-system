import { useEffect, useState } from 'react';
import { createSchedule, deleteSchedule, getAirlines, getSchedules, updateSchedule } from '../services/api';
import type { AirlineRs, ScheduleRs } from '../types';

function apiErr(e: unknown) { return e instanceof Error ? e.message : String(e); }

const EMPTY = {
  flightNumber: '',
  originAirport: '',
  destinationAirport: '',
  scheduledDeparture: '',
  scheduledArrival: '',
  airlineId: '',
};

export default function SchedulesPage() {
  const [items, setItems] = useState<ScheduleRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [form, setForm] = useState(EMPTY);
  const [editing, setEditing] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    getSchedules(search ? { search } : undefined)
      .then(setItems)
      .catch(e => setError(apiErr(e)));

  useEffect(() => { load(); getAirlines().then(setAirlines); }, []);

  async function save() {
    setError(null);
    const body = { ...form, airlineId: Number(form.airlineId) };
    try {
      if (editing != null) await updateSchedule(editing, body);
      else await createSchedule(body);
      setForm(EMPTY); setEditing(null); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function remove(id: number) {
    if (!confirm('Удалить расписание?')) return;
    try { await deleteSchedule(id); load(); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  function startEdit(s: ScheduleRs) {
    setEditing(s.scheduleId);
    setForm({
      flightNumber: s.flightNumber,
      originAirport: s.originAirport.trim(),
      destinationAirport: s.destinationAirport.trim(),
      scheduledDeparture: s.scheduledDeparture?.slice(0, 16) ?? '',
      scheduledArrival: s.scheduledArrival?.slice(0, 16) ?? '',
      airlineId: String(s.airline?.airlineId ?? ''),
    });
  }

  function f(v: string) { return v.replace('T', ' ').slice(0, 16); }

  return (
    <div className="page">
      <h1>Расписание</h1>
      {error && <p className="page-error">{error}</p>}

      <div className="form-row" style={{ marginBottom: 8 }}>
        <input placeholder="Поиск по номеру рейса…" value={search}
          onChange={e => setSearch(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && load()} />
        <button className="btn-ghost btn-sm" onClick={load}>Найти</button>
      </div>

      <div className="form-row">
        <input placeholder="Номер рейса" value={form.flightNumber}
          onChange={e => setForm(p => ({ ...p, flightNumber: e.target.value }))} />
        <input placeholder="Откуда (IATA)" maxLength={3} value={form.originAirport}
          onChange={e => setForm(p => ({ ...p, originAirport: e.target.value.toUpperCase() }))} />
        <input placeholder="Куда (IATA)" maxLength={3} value={form.destinationAirport}
          onChange={e => setForm(p => ({ ...p, destinationAirport: e.target.value.toUpperCase() }))} />
        <input type="datetime-local" value={form.scheduledDeparture}
          onChange={e => setForm(p => ({ ...p, scheduledDeparture: e.target.value }))} />
        <input type="datetime-local" value={form.scheduledArrival}
          onChange={e => setForm(p => ({ ...p, scheduledArrival: e.target.value }))} />
        <select value={form.airlineId}
          onChange={e => setForm(p => ({ ...p, airlineId: e.target.value }))}>
          <option value="">— авиакомпания —</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>
              {a.iataCode} — {a.name}
            </option>
          ))}
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
        <thead>
          <tr>
            <th>Рейс</th><th>Откуда</th><th>Куда</th>
            <th>Вылет</th><th>Прилёт</th><th>Авиакомпания</th><th></th>
          </tr>
        </thead>
        <tbody>
          {items.map(s => (
            <tr key={s.scheduleId}>
              <td>{s.flightNumber}</td>
              <td>{s.originAirport.trim()}</td>
              <td>{s.destinationAirport.trim()}</td>
              <td>{f(s.scheduledDeparture)}</td>
              <td>{f(s.scheduledArrival)}</td>
              <td>{s.airline?.name ?? '—'}</td>
              <td className="cell-actions">
                <button className="btn-ghost btn-sm" onClick={() => startEdit(s)}>✏</button>
                <button className="btn-danger btn-sm" onClick={() => remove(s.scheduleId)}>✕</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
