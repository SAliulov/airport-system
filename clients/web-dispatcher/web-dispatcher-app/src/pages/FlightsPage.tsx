import { useEffect, useState } from 'react';
import {
  assignAircraft,
  assignGate,
  createDelayWarning,
  createFlight,
  deleteFlight,
  getAircraftTypes,
  getAirlines,
  getFlightById,
  getFlights,
  getGates,
  updateFlightStatus,
} from '../services/api';
import type { AircraftTypeRs, AirlineRs, FlightRs, GateRs } from '../types';

function apiErr(e: unknown) { return e instanceof Error ? e.message : String(e); }
function f(v?: string) { return v ? v.replace('T', ' ').slice(0, 16) : '—'; }

const STATUSES = ['SCHEDULED', 'DEPARTED', 'ARRIVED', 'DELAYED', 'CANCELLED'];

export default function FlightsPage() {
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [aircraftTypes, setAircraftTypes] = useState<AircraftTypeRs[]>([]);
  const [gates, setGates] = useState<GateRs[]>([]);
  const [error, setError] = useState<string | null>(null);

  // filters
  const [filterDate, setFilterDate] = useState('');
  const [filterStatus, setFilterStatus] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [filterDirection, setFilterDirection] = useState('');

  // selected flight for detail panel
  const [selected, setSelected] = useState<FlightRs | null>(null);

  // forms
  const [newScheduleId, setNewScheduleId] = useState('');
  const [statusNew, setStatusNew] = useState('');
  const [acType, setAcType] = useState('');
  const [gateForm, setGateForm] = useState({ gateId: '', assignedFrom: '', assignedTo: '' });
  const [delayForm, setDelayForm] = useState({ delayMinutes: '', reason: '' });

  const load = () => {
    const params: Record<string, string> = {};
    if (filterDate) params.date = filterDate;
    if (filterStatus) params.status = filterStatus;
    if (filterAirline) params.airline = filterAirline;
    if (filterDirection.length === 3) params.direction = filterDirection;
    getFlights(params).then(setFlights).catch(e => setError(apiErr(e)));
  };

  useEffect(() => {
    load();
    getAirlines().then(setAirlines);
    getAircraftTypes().then(setAircraftTypes);
    getGates().then(setGates);
  }, []);

  async function openDetail(f: FlightRs) {
    try { setSelected(await getFlightById(f.flightId)); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  async function addFlight() {
    if (!newScheduleId) return;
    try { await createFlight({ scheduleId: Number(newScheduleId) }); setNewScheduleId(''); load(); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  async function removeFlight(id: number) {
    if (!confirm('Удалить рейс?')) return;
    try { await deleteFlight(id); setSelected(null); load(); }
    catch (e: unknown) { setError(apiErr(e)); }
  }

  async function changeStatus() {
    if (!selected || !statusNew) return;
    try {
      const updated = await updateFlightStatus(selected.flightId, statusNew);
      setSelected(updated); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function changeAircraft() {
    if (!selected || !acType) return;
    try {
      const updated = await assignAircraft(selected.flightId, Number(acType));
      setSelected(updated); load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function changeGate() {
    if (!selected || !gateForm.gateId) return;
    try {
      await assignGate(selected.flightId, {
        gateId: Number(gateForm.gateId),
        assignedFrom: gateForm.assignedFrom || undefined,
        assignedTo: gateForm.assignedTo || undefined,
      });
      setSelected(await getFlightById(selected.flightId));
      load();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  async function addDelay() {
    if (!selected || !delayForm.delayMinutes) return;
    try {
      await createDelayWarning(selected.flightId, {
        delayMinutes: Number(delayForm.delayMinutes),
        reason: delayForm.reason || undefined,
      });
      setSelected(await getFlightById(selected.flightId));
      setDelayForm({ delayMinutes: '', reason: '' });
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  const statusColor: Record<string, string> = {
    SCHEDULED: '#3b82f6', DEPARTED: '#f97316',
    ARRIVED: '#22c55e', DELAYED: '#ef4444', CANCELLED: '#6b7280',
  };

  return (
    <div className="page flights-layout">
      <div className="flights-list">
        <h1>Рейсы</h1>
        {error && <p className="page-error">{error}</p>}

        {/* Filters */}
        <div className="form-row" style={{ flexWrap: 'wrap', gap: 8 }}>
          <input type="date" value={filterDate}
            onChange={e => setFilterDate(e.target.value)} />
          <select value={filterStatus} onChange={e => setFilterStatus(e.target.value)}>
            <option value="">Все статусы</option>
            {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
          </select>
          <select value={filterAirline} onChange={e => setFilterAirline(e.target.value)}>
            <option value="">Все авиакомпании</option>
            {airlines.map(a => (
              <option key={a.airlineId} value={String(a.airlineId)}>{a.iataCode} — {a.name}</option>
            ))}
          </select>
          <input placeholder="Направление (IATA)" maxLength={3} style={{ width: 120 }}
            value={filterDirection}
            onChange={e => setFilterDirection(e.target.value.toUpperCase())} />
          <button className="btn-primary btn-sm" onClick={load}>Применить</button>
        </div>

        {/* Add flight */}
        <div className="form-row" style={{ marginTop: 8 }}>
          <input placeholder="ID расписания" type="number" min={1}
            value={newScheduleId}
            onChange={e => setNewScheduleId(e.target.value)} />
          <button className="btn-primary btn-sm" onClick={addFlight}>+ Рейс</button>
        </div>

        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th><th>Рейс</th><th>Маршрут</th><th>Вылет</th><th>Статус</th><th>Гейт</th><th></th>
            </tr>
          </thead>
          <tbody>
            {flights.map(fl => {
              const gate = fl.currentGateAssignment?.gate;
              return (
                <tr
                  key={fl.flightId}
                  className={selected?.flightId === fl.flightId ? 'row-selected' : ''}
                  onClick={() => openDetail(fl)}
                  style={{ cursor: 'pointer' }}
                >
                  <td>{fl.flightId}</td>
                  <td>{fl.schedule.flightNumber}</td>
                  <td>{fl.schedule.originAirport.trim()} → {fl.schedule.destinationAirport.trim()}</td>
                  <td>{f(fl.schedule.scheduledDeparture)}</td>
                  <td>
                    <span style={{
                      color: statusColor[fl.status] ?? '#fff',
                      fontWeight: 600,
                    }}>{fl.status}</span>
                  </td>
                  <td>{gate ? gate.gateNumber : '—'}</td>
                  <td className="cell-actions" onClick={e => e.stopPropagation()}>
                    <button className="btn-danger btn-sm" onClick={() => removeFlight(fl.flightId)}>✕</button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Detail panel */}
      {selected && (
        <div className="flight-detail-panel">
          <div className="panel-header">
            <strong>Рейс #{selected.flightId} — {selected.schedule.flightNumber}</strong>
            <button className="btn-ghost btn-sm" onClick={() => setSelected(null)}>✕</button>
          </div>

          {/* Status */}
          <section className="panel-section">
            <h3>Статус</h3>
            <div className="form-row">
              <select value={statusNew} onChange={e => setStatusNew(e.target.value)}>
                <option value="">— выбрать —</option>
                {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
              </select>
              <button className="btn-primary btn-sm" onClick={changeStatus}>Изменить</button>
            </div>
            <p>Текущий: <strong style={{ color: statusColor[selected.status] }}>{selected.status}</strong></p>
          </section>

          {/* Aircraft */}
          <section className="panel-section">
            <h3>Тип ВС</h3>
            <div className="form-row">
              <select value={acType} onChange={e => setAcType(e.target.value)}>
                <option value="">— выбрать —</option>
                {aircraftTypes.map(a => (
                  <option key={a.aircraftTypeId} value={String(a.aircraftTypeId)}>
                    {a.icaoCode} ({a.sizeCategory})
                  </option>
                ))}
              </select>
              <button className="btn-primary btn-sm" onClick={changeAircraft}>Назначить</button>
            </div>
            <p>Текущий: <strong>{selected.aircraftType?.icaoCode ?? '—'}</strong></p>
          </section>

          {/* Gate */}
          <section className="panel-section">
            <h3>Гейт</h3>
            <div className="form-row">
              <select value={gateForm.gateId}
                onChange={e => setGateForm(g => ({ ...g, gateId: e.target.value }))}>
                <option value="">— выбрать гейт —</option>
                {gates.filter(g => g.isActive).map(g => (
                  <option key={g.gateId} value={String(g.gateId)}>
                    {g.gateNumber}{g.terminal ? ` (${g.terminal})` : ''}
                  </option>
                ))}
              </select>
            </div>
            <div className="form-row">
              <label>С <input type="datetime-local" value={gateForm.assignedFrom}
                onChange={e => setGateForm(g => ({ ...g, assignedFrom: e.target.value }))} /></label>
              <label>По <input type="datetime-local" value={gateForm.assignedTo}
                onChange={e => setGateForm(g => ({ ...g, assignedTo: e.target.value }))} /></label>
              <button className="btn-primary btn-sm" onClick={changeGate}>Назначить</button>
            </div>
            <p>Текущий: <strong>{selected.currentGateAssignment?.gate?.gateNumber ?? '—'}</strong></p>
          </section>

          {/* Delays */}
          <section className="panel-section">
            <h3>Задержки</h3>
            <div className="form-row">
              <input placeholder="Минуты" type="number" min={1} style={{ width: 90 }}
                value={delayForm.delayMinutes}
                onChange={e => setDelayForm(d => ({ ...d, delayMinutes: e.target.value }))} />
              <input placeholder="Причина" value={delayForm.reason}
                onChange={e => setDelayForm(d => ({ ...d, reason: e.target.value }))} />
              <button className="btn-danger btn-sm" onClick={addDelay}>Добавить</button>
            </div>
            {(selected.delayWarnings ?? []).length === 0
              ? <p className="muted">Нет задержек</p>
              : (selected.delayWarnings ?? []).map(w => (
                  <div key={w.warningId} className="delay-row">
                    ⚠ {w.delayMinutes} мин — {w.reason ?? 'причина не указана'}
                  </div>
                ))}
          </section>
        </div>
      )}
    </div>
  );
}
