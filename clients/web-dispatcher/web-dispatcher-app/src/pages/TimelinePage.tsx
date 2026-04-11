import { useEffect, useState } from 'react';
import { exportUrl, getTimeline } from '../services/api';
import { getToken } from '../services/auth';
import type { GateTimelineSegmentRs } from '../types';

function apiErr(e: unknown) { return e instanceof Error ? e.message : String(e); }

function todayIso() { return new Date().toISOString().slice(0, 10); }

const COLORS = [
  '#3b82f6', '#f97316', '#22c55e', '#a855f7',
  '#06b6d4', '#ec4899', '#facc15', '#84cc16',
];

export default function TimelinePage() {
  const [date, setDate] = useState(todayIso());
  const [segments, setSegments] = useState<GateTimelineSegmentRs[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () =>
    getTimeline(date)
      .then(setSegments)
      .catch(e => setError(apiErr(e)));

  useEffect(() => { load(); }, [date]);

  // Grouping by gate
  const byGate = new Map<string, { label: string; segs: GateTimelineSegmentRs[] }>();
  for (const s of segments) {
    const key = String(s.gateId);
    if (!byGate.has(key)) {
      byGate.set(key, {
        label: s.terminal ? `${s.gateNumber} (${s.terminal})` : s.gateNumber,
        segs: [],
      });
    }
    byGate.get(key)!.segs.push(s);
  }

  // Find visible window [dayStart, dayEnd]
  const dayStart = new Date(`${date}T00:00:00`).getTime();
  const dayEnd = new Date(`${date}T23:59:59`).getTime();
  const span = dayEnd - dayStart;

  function pct(iso: string) {
    const t = new Date(iso).getTime();
    return Math.max(0, Math.min(100, ((t - dayStart) / span) * 100));
  }
  function width(from: string, to: string) {
    const a = new Date(from).getTime();
    const b = new Date(to).getTime();
    return Math.max(0.5, ((b - a) / span) * 100);
  }

  async function downloadExport(format: 'pdf' | 'excel') {
    const token = getToken();
    const url = exportUrl(format, date);
    try {
      const res = await fetch(url, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) { setError(`Экспорт: HTTP ${res.status}`); return; }
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = `schedule_${date}.${format === 'pdf' ? 'pdf' : 'xlsx'}`;
      a.click();
    } catch (e: unknown) { setError(apiErr(e)); }
  }

  const flightColors = new Map<number, string>();
  let colorIdx = 0;
  for (const s of segments) {
    if (!flightColors.has(s.flightId)) {
      flightColors.set(s.flightId, COLORS[colorIdx % COLORS.length]);
      colorIdx++;
    }
  }

  return (
    <div className="page">
      <div className="timeline-toolbar">
        <h1>Таймлайн гейтов</h1>
        <input type="date" value={date} onChange={e => setDate(e.target.value)} />
        <button className="btn-ghost btn-sm" onClick={load}>Обновить</button>
        <button className="btn-primary btn-sm" onClick={() => downloadExport('pdf')}>
          ⬇ PDF
        </button>
        <button className="btn-primary btn-sm" onClick={() => downloadExport('excel')}>
          ⬇ Excel
        </button>
      </div>

      {error && <p className="page-error">{error}</p>}

      {/* Hour ticks */}
      <div className="tl-hour-row">
        {Array.from({ length: 25 }, (_, i) => (
          <span key={i} style={{ left: `${(i / 24) * 100}%` }}>
            {String(i).padStart(2, '0')}:00
          </span>
        ))}
      </div>

      <div className="tl-container">
        {byGate.size === 0 && <p className="muted">Нет данных за выбранную дату</p>}
        {[...byGate.entries()].map(([key, { label, segs }]) => (
          <div key={key} className="tl-row">
            <div className="tl-gate-label">{label}</div>
            <div className="tl-bar-area">
              {segs.map(s => (
                <div
                  key={s.flightId + s.assignedFrom}
                  className="tl-segment"
                  style={{
                    left: `${pct(s.assignedFrom)}%`,
                    width: `${width(s.assignedFrom, s.assignedTo)}%`,
                    background: flightColors.get(s.flightId) ?? '#3b82f6',
                  }}
                  title={`${s.flightNumber}\n${s.assignedFrom.slice(11, 16)} – ${s.assignedTo.slice(11, 16)}`}
                >
                  <span className="tl-label">{s.flightNumber}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
