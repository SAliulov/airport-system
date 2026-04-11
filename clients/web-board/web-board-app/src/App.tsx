import { useCallback, useEffect, useRef, useState } from 'react';
import { fetchAirlines, fetchFlights } from './api';
import { FilterBar } from './components/FilterBar';
import { FlightTable } from './components/FlightTable';
import { useStomp } from './hooks/useStomp';
import type { AirlineRs, FlightRs } from './types';
function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function useClock() {
  const [time, setTime] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return time;
}

export default function App() {
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [date, setDate] = useState(todayIso());
  const [direction, setDirection] = useState('');
  const [airlineId, setAirlineId] = useState('');

  // ids рейсов, получивших push последние 8 секунд
  const [highlightIds, setHighlightIds] = useState<Set<number>>(new Set());
  const highlightTimers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const now = useClock();
  const flightsRef = useRef<FlightRs[]>([]);
  flightsRef.current = flights;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchFlights({
        date: date || undefined,
        direction: direction.length === 3 ? direction : undefined,
        airline: airlineId ? Number(airlineId) : undefined,
      });
      setFlights(data);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, [date, direction, airlineId]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    fetchAirlines().then(setAirlines).catch(() => {});
  }, []);

  function highlight(flightId: number) {
    setHighlightIds(prev => new Set(prev).add(flightId));
    const prev = highlightTimers.current.get(flightId);
    if (prev) clearTimeout(prev);
    const timer = setTimeout(() => {
      setHighlightIds(s => { const next = new Set(s); next.delete(flightId); return next; });
      highlightTimers.current.delete(flightId);
    }, 8000);
    highlightTimers.current.set(flightId, timer);
  }

  const handleMessage = useCallback((body: string) => {
    try {
      const msg = JSON.parse(body) as { flightId?: number; status?: string };
      if (!msg.flightId) return;
      highlight(msg.flightId);
      // Обновить статус в текущем списке без перезагрузки
      if (msg.status) {
        setFlights(prev =>
          prev.map(f =>
            f.flightId === msg.flightId
              ? { ...f, status: msg.status as FlightRs['status'] }
              : f,
          ),
        );
      }
    } catch {
      // ignore malformed
    }
  }, []);

  useStomp(['/topic/flights', '/topic/delays', '/topic/gate-changes'], handleMessage);

  function handleFilterChange(field: 'date' | 'direction' | 'airlineId', value: string) {
    if (field === 'date') setDate(value);
    else if (field === 'direction') setDirection(value);
    else setAirlineId(value);
  }

  return (
    <div className="board">
      <header className="board-header">
        <div className="board-title">
          <span className="board-icon">✈</span>
          Информационное табло
        </div>
        <div className="board-clock">
          {now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
        </div>
      </header>

      <FilterBar
        date={date}
        direction={direction}
        airlineId={airlineId}
        airlines={airlines}
        onChange={handleFilterChange}
      />

      <main className="board-main">
        {loading && <p className="board-loading">Загрузка…</p>}
        {error && <p className="board-error">{error}</p>}
        {!loading && !error && (
          <FlightTable flights={flights} highlightIds={highlightIds} />
        )}
      </main>
    </div>
  );
}
