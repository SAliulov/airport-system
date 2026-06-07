import { useCallback, useEffect, useMemo, useState } from 'react';
import { fetchFlightsPage } from '../api';
import type { FlightRs } from '../types';
import { shiftAirportDate } from '../utils/airportTime';
import { BOARD_PAGE_SIZE, type BoardFilterState, type DayPreset } from '../constants/boardFilters';

function dateForPreset(preset: DayPreset, today: string): string {
  if (preset === 'yesterday') return shiftAirportDate(today, -1);
  if (preset === 'tomorrow') return shiftAirportDate(today, 1);
  return today;
}

export interface UseBoardFlightsOptions {
  filters: BoardFilterState;
  debouncedSearch: string;
  /** Текущий календарный день MSK — для пересчёта «Сегодня» в полночь. */
  airportToday: string;
}

/**
 * Пагинированная загрузка рейсов для табло с единым effect при смене фильтров.
 */
export function useBoardFlights({ filters, debouncedSearch, airportToday }: UseBoardFlightsOptions) {
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const operationDate = useMemo(
    () => dateForPreset(filters.dayPreset, airportToday),
    [filters.dayPreset, airportToday],
  );

  const load = useCallback(
    async (pageOverride = 0) => {
      setLoading(true);
      setError(null);
      try {
        const result = await fetchFlightsPage({
          date: operationDate,
          search: debouncedSearch || undefined,
          status: filters.status,
          origin: filters.origin.length === 3 ? filters.origin : undefined,
          destination: filters.destination.length === 3 ? filters.destination : undefined,
          airline: filters.airlineId ? Number(filters.airlineId) : undefined,
          terminal: filters.terminal || undefined,
          hourFrom: filters.hourFrom ?? undefined,
          page: pageOverride,
          size: BOARD_PAGE_SIZE,
        });
        setFlights(result.content);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      } catch (e) {
        setError(String(e));
      } finally {
        setLoading(false);
      }
    },
    [
      debouncedSearch,
      filters.airlineId,
      filters.destination,
      filters.hourFrom,
      filters.origin,
      filters.status,
      filters.terminal,
      operationDate,
    ],
  );

  useEffect(() => {
    setPage(0);
    void load(0);
  }, [
    filters.dayPreset,
    filters.hourFrom,
    filters.terminal,
    debouncedSearch,
    filters.status,
    filters.origin,
    filters.destination,
    filters.airlineId,
    operationDate,
    load,
  ]);

  useEffect(() => {
    if (page > 0) void load(page);
  }, [page, load]);

  return {
    flights,
    setFlights,
    page,
    setPage,
    totalPages,
    totalElements,
    pageSize: BOARD_PAGE_SIZE,
    loading,
    error,
    operationDate,
  };
}
