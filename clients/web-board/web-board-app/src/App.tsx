import { useEffect, useState } from 'react';
import { useAirportConfig } from '../../../shared/context/AirportConfigProvider';
import { ConnectionStatusBanner } from '../../../shared/components/ConnectionStatusBanner';
import { fetchAirlines, fetchGates } from './api';
import { FilterBar } from './components/FilterBar';
import { FlightTable } from './components/FlightTable';
import { PaginationBar } from './components/PaginationBar';
import { EMPTY_BOARD_FILTERS, type BoardFilterState } from './constants/boardFilters';
import { useAirportClock } from './hooks/useAirportClock';
import { useBoardFlights } from './hooks/useBoardFlights';
import { useDebouncedValue } from './hooks/useDebouncedValue';
import { useFlightHighlight } from './hooks/useFlightHighlight';
import { useFlightsRealtime } from './hooks/useFlightsRealtime';
import type { AirlineRs } from './types';
import { brandingLogoUrl } from './utils/branding';

/** Корневой компонент Web Board. */
export default function App() {
  const airport = useAirportConfig();
  const [filters, setFilters] = useState<BoardFilterState>(EMPTY_BOARD_FILTERS);
  const debouncedSearch = useDebouncedValue(filters.search.trim(), 400);
  const { airportToday, timeLabel } = useAirportClock();

  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [terminals, setTerminals] = useState<string[]>([]);
  const [refsError, setRefsError] = useState<string | null>(null);

  const board = useBoardFlights({ filters, debouncedSearch, airportToday });
  const { highlightIds, highlight } = useFlightHighlight();

  const { connected } = useFlightsRealtime({ setFlights: board.setFlights, highlight });

  useEffect(() => {
    fetchAirlines()
      .then(setAirlines)
      .catch(e => setRefsError(`Не удалось загрузить авиакомпании: ${String(e)}`));
    fetchGates()
      .then(gates => {
        const terms = [...new Set(
          gates.filter(g => g.isActive !== false).map(g => g.terminal?.trim()).filter((t): t is string => Boolean(t)),
        )].sort();
        setTerminals(terms);
      })
      .catch(e => setRefsError(`Не удалось загрузить гейты: ${String(e)}`));
  }, []);

  function patchFilters(patch: Partial<BoardFilterState>) {
    setFilters(prev => ({ ...prev, ...patch }));
  }

  return (
    <div className="board">
      <header className="board-header">
        <div className="board-title">
          <img src={brandingLogoUrl()} alt="АСУРР" className="board-logo" />
          Табло аэропорта {airport.homeIata}
        </div>
        <div className="board-clock">{timeLabel}</div>
      </header>

      <ConnectionStatusBanner connected={connected} />

      <FilterBar
        filters={filters}
        airlines={airlines}
        terminals={terminals}
        homeIata={airport.homeIata}
        onChange={patchFilters}
      />

      <main className="board-main">
        {board.loading && <p className="board-loading">Загрузка…</p>}
        {refsError && <p className="board-error">{refsError}</p>}
        {board.error && <p className="board-error">{board.error}</p>}
        {!board.loading && !board.error && (
          <>
            <PaginationBar
              page={board.page}
              totalPages={board.totalPages}
              totalElements={board.totalElements}
              pageSize={board.pageSize}
              onPageChange={board.setPage}
              buttonClassName="preset-btn"
            />
            <FlightTable flights={board.flights} highlightIds={highlightIds} />
            <PaginationBar
              page={board.page}
              totalPages={board.totalPages}
              totalElements={board.totalElements}
              pageSize={board.pageSize}
              onPageChange={board.setPage}
              buttonClassName="preset-btn"
            />
          </>
        )}
      </main>
    </div>
  );
}
