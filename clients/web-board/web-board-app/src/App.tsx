import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { fetchAirlines, fetchFlights } from './api';
import { FilterBar, type DatePreset } from './components/FilterBar';
import { FlightTable } from './components/FlightTable';
import { useStomp } from './hooks/useStomp';
import type { AirlineRs, FlightRs, GateAssignmentPush } from './types';
import { brandingLogoUrl } from './utils/branding';
import { isWithinDaysFromToday, scheduleDepartureDate, todayAirportDate } from './utils/airportTime';

function useClock() {
  const [time, setTime] = useState(() => new Date());
  useEffect(() => {
    const id = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(id);
  }, []);
  return time;
}

function applyDatePreset(preset: DatePreset): string {
  if (preset === 'today') return todayAirportDate();
  return '';
}

export default function App() {
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [datePreset, setDatePreset] = useState<DatePreset>('today');
  const [date, setDate] = useState(todayAirportDate());
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [airlineId, setAirlineId] = useState('');

  const [highlightIds, setHighlightIds] = useState<Set<number>>(new Set());
  const highlightTimers = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const now = useClock();
  const flightsRef = useRef<FlightRs[]>([]);
  flightsRef.current = flights;

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const apiDate = datePreset === 'today' ? date || todayAirportDate() : undefined;
      const data = await fetchFlights({
        date: apiDate,
        origin: origin.length === 3 ? origin : undefined,
        destination: destination.length === 3 ? destination : undefined,
        airline: airlineId ? Number(airlineId) : undefined,
      });
      setFlights(data);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, [airlineId, date, datePreset, destination, origin]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    fetchAirlines().then(setAirlines).catch(() => {});
  }, []);

  const displayedFlights = useMemo(() => {
    if (datePreset !== '7d' && datePreset !== '30d') return flights;
    const days = datePreset === '7d' ? 7 : 30;
    const today = todayAirportDate();
    return flights.filter(f => {
      const depDay = scheduleDepartureDate(f.scheduledDeparture) ?? f.operationDate;
      return depDay != null && isWithinDaysFromToday(depDay, days, today);
    });
  }, [datePreset, flights]);

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

  const handleMessage = useCallback((body: string, topic: string) => {
    try {
      const msg = JSON.parse(body);
      if (topic === '/topic/flights') {
        const flight = msg as FlightRs;
        if (!flight.flightId) return;
        highlight(flight.flightId);
        setFlights(prev =>
          prev.map(f =>
            f.flightId === flight.flightId
              ? {
                  ...f,
                  status: flight.status ?? f.status,
                  actualDeparture: flight.actualDeparture ?? f.actualDeparture,
                  actualArrival: flight.actualArrival ?? f.actualArrival,
                }
              : f,
          ),
        );
      } else if (topic === '/topic/gate-changes') {
        const push = msg as GateAssignmentPush;
        if (!push.flightId) return;
        highlight(push.flightId);
        setFlights(prev =>
          prev.map(f =>
            f.flightId === push.flightId
              ? { ...f, currentGateAssignment: push.assignment }
              : f,
          ),
        );
      } else if (topic === '/topic/delays') {
        const push = msg as { flightId?: number };
        if (!push.flightId) return;
        highlight(push.flightId);
      }
    } catch {
      // ignore malformed
    }
  }, []);

  useStomp(['/topic/flights', '/topic/delays', '/topic/gate-changes'], handleMessage);

  function handleDatePresetChange(preset: DatePreset) {
    setDatePreset(preset);
    setDate(applyDatePreset(preset));
  }

  return (
    <div className="board">
      <header className="board-header">
        <div className="board-title">
          <img src={brandingLogoUrl()} alt="АСУРР" className="board-logo" />
          Табло аэропорта Шереметьево (SVO)
        </div>
        <div className="board-clock">
          {now.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
        </div>
      </header>

      <FilterBar
        date={date}
        datePreset={datePreset}
        origin={origin}
        destination={destination}
        airlineId={airlineId}
        airlines={airlines}
        onDatePresetChange={handleDatePresetChange}
        onDateChange={setDate}
        onOriginChange={setOrigin}
        onDestinationChange={setDestination}
        onAirlineChange={setAirlineId}
      />

      <main className="board-main">
        {loading && <p className="board-loading">Загрузка…</p>}
        {error && <p className="board-error">{error}</p>}
        {!loading && !error && (
          <FlightTable flights={displayedFlights} highlightIds={highlightIds} />
        )}
      </main>
    </div>
  );
}
