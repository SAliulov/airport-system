import { useCallback, useEffect, useMemo, useState } from 'react';
import PageStatus from '../components/PageStatus';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import { getGates, getTimeline } from '../services/api';
import type { GateRs, GateTimelineSegmentRs } from '../types';
import { formatApiError } from '../utils/apiError';
import { todayAirportDate } from '../utils/airportTime';

interface ClippedSegment extends GateTimelineSegmentRs {
  clipFrom: string;
  clipTo: string;
}

function parseWallMs(iso: string): number {
  const normalized = iso.includes('T') ? iso : iso.replace(' ', 'T');
  const d = new Date(normalized);
  return Number.isNaN(d.getTime()) ? 0 : d.getTime();
}

function segKey(s: GateTimelineSegmentRs) {
  return `${s.flightId}-${s.assignedFrom}`;
}

function clipToDay(
  segs: GateTimelineSegmentRs[],
  dayStartMs: number,
  dayEndMs: number,
): ClippedSegment[] {
  const clipped: ClippedSegment[] = [];
  for (const s of segs) {
    const fromMs = parseWallMs(s.assignedFrom);
    const toMs = parseWallMs(s.assignedTo);
    const clipStart = Math.max(fromMs, dayStartMs);
    const clipEnd = Math.min(toMs, dayEndMs);
    if (clipStart >= clipEnd) continue;
    clipped.push({
      ...s,
      clipFrom: new Date(clipStart).toISOString(),
      clipTo: new Date(clipEnd).toISOString(),
    });
  }
  return clipped;
}

export default function TimelinePage() {
  const [date, setDate] = useState(todayAirportDate);
  const [segments, setSegments] = useState<GateTimelineSegmentRs[]>([]);
  const [gates, setGates] = useState<GateRs[]>([]);
  const [pageError, setPageError] = useState<string | null>(null);

  const dayStartMs = useMemo(
    () => parseWallMs(`${date}T00:00:00`),
    [date],
  );
  const dayEndMs = useMemo(
    () => parseWallMs(`${date}T23:59:59.999`),
    [date],
  );
  const span = dayEndMs - dayStartMs;

  const load = useCallback(() => {
    setPageError(null);
    Promise.all([getTimeline(date), getGates()])
      .then(([timeline, allGates]) => {
        setSegments(timeline);
        setGates(allGates.filter(g => g.isActive));
      })
      .catch(e => setPageError(formatApiError(e)));
  }, [date]);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, load);

  useEffect(() => {
    load();
  }, [load]);

  const byGate = useMemo(() => {
    const map = new Map<string, { label: string; segs: GateTimelineSegmentRs[] }>();
    for (const g of gates) {
      const key = String(g.gateId);
      const term = g.terminal ? ` (${g.terminal})` : '';
      map.set(key, { label: `${g.gateNumber}${term}`, segs: [] });
    }
    for (const s of segments) {
      const key = String(s.gateId);
      if (!map.has(key)) {
        map.set(key, {
          label: s.terminal ? `${s.gateNumber} (${s.terminal})` : s.gateNumber,
          segs: [],
        });
      }
      map.get(key)!.segs.push(s);
    }
    return map;
  }, [gates, segments]);

  function pct(iso: string) {
    const t = parseWallMs(iso);
    return Math.max(0, Math.min(100, ((t - dayStartMs) / span) * 100));
  }

  function width(from: string, to: string) {
    const a = parseWallMs(from);
    const b = parseWallMs(to);
    return Math.max(0.5, ((b - a) / span) * 100);
  }

  const STATUS_COLORS: Record<string, string> = {
    SCHEDULED: '#3b82f6',
    DEPARTED: '#f97316',
    ARRIVED: '#22c55e',
    DELAYED: '#ef4444',
    CANCELLED: '#6b7280',
  };

  function segColor(s: ClippedSegment): string {
    const st = s.flightStatus;
    return st ? (STATUS_COLORS[st] ?? '#3b82f6') : '#3b82f6';
  }

  const gateRows = [...byGate.entries()].sort((a, b) => a[1].label.localeCompare(b[1].label));

  return (
    <div className="page">
      <div className="timeline-toolbar">
        <h1>Таймлайн гейтов</h1>
        <input type="date" value={date} onChange={e => setDate(e.target.value)} />
        <button type="button" className="btn-ghost btn-sm" onClick={() => setDate(todayAirportDate())}>
          Сегодня
        </button>
      </div>

      <PageStatus error={pageError} waitingForServer={waitingForServer} />

      <div className="tl-legend">
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#1e293b', border: '1px solid #334155' }} />
          Свободен
        </span>
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#3b82f6' }} /> SCHEDULED
        </span>
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#f97316' }} /> DEPARTED
        </span>
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#22c55e' }} /> ARRIVED
        </span>
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#ef4444' }} /> DELAYED
        </span>
        <span className="tl-legend-item">
          <span className="tl-legend-dot" style={{ background: '#6b7280' }} /> CANCELLED
        </span>
      </div>

      <div className="tl-hour-row">
        {Array.from({ length: 25 }, (_, i) => (
          <span key={i} style={{ left: `${(i / 24) * 100}%` }}>
            {String(i).padStart(2, '0')}:00
          </span>
        ))}
      </div>

      <div className="tl-container">
        {gateRows.length === 0 && <p className="muted">Нет активных гейтов</p>}
        {gateRows.map(([key, { label, segs }]) => {
          const clipped = clipToDay(segs, dayStartMs, dayEndMs);
          return (
            <div key={key} className="tl-row">
              <div className="tl-gate-label">{label}</div>
              <div className="tl-bar-area">
                {clipped.map(s => (
                  <div
                    key={segKey(s)}
                    className="tl-segment"
                    style={{
                      left: `${pct(s.clipFrom)}%`,
                      width: `${width(s.clipFrom, s.clipTo)}%`,
                      background: segColor(s),
                    }}
                    title={`${s.flightNumber}${s.flightStatus ? ` (${s.flightStatus})` : ''}\n${s.assignedFrom.slice(11, 16)} – ${s.assignedTo.slice(11, 16)}`}
                  >
                    <span className="tl-label">{s.flightNumber}</span>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
