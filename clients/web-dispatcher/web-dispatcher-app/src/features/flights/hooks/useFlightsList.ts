import { useCallback, useEffect, useState } from 'react';
import {
  getAllFlights,
  getFilteredFlights,
  searchFlights,
  type FlightListParams,
} from '../../../services/api';
import { formatApiError } from '../../../utils/apiError';
import type { FlightRs } from '../../../types';
import { FLIGHTS_PAGE_SIZE } from '../constants';

export interface FlightsListFilters {
  filterDate: string;
  filterStatus: string;
  filterAirline: string;
  filterOrigin: string;
  filterDestination: string;
  debouncedSearch: string;
  sortOrder: 'asc' | 'desc';
}

/**
 * Загрузка пагинированного списка рейсов с единым effect при смене фильтров или страницы.
 */
export function useFlightsList(filters: FlightsListFilters) {
  const [flights, setFlights] = useState<FlightRs[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [pageError, setPageError] = useState<string | null>(null);

  const buildParams = useCallback(
    (pageOverride: number, dateOverride?: string): FlightListParams => ({
      page: pageOverride,
      size: FLIGHTS_PAGE_SIZE,
      sort: filters.sortOrder,
      date: (dateOverride ?? filters.filterDate) || undefined,
      status: filters.filterStatus || undefined,
      airline: filters.filterAirline ? Number(filters.filterAirline) : undefined,
      origin: filters.filterOrigin.length === 3 ? filters.filterOrigin : undefined,
      destination: filters.filterDestination.length === 3 ? filters.filterDestination : undefined,
    }),
    [
      filters.debouncedSearch,
      filters.filterAirline,
      filters.filterDate,
      filters.filterDestination,
      filters.filterOrigin,
      filters.filterStatus,
      filters.sortOrder,
    ],
  );

  const load = useCallback(
    (dateOverride?: string, pageOverride = 0) => {
      setPageError(null);
      const listParams = buildParams(pageOverride, dateOverride);

      const request = filters.debouncedSearch
        ? searchFlights(filters.debouncedSearch, listParams)
        : listParams.date || listParams.status || listParams.airline || listParams.origin || listParams.destination
          ? getFilteredFlights(listParams)
          : getAllFlights(listParams);

      return request
        .then(result => {
          setFlights(result.content);
          setPage(result.page);
          setTotalPages(result.totalPages);
          setTotalElements(result.totalElements);
        })
        .catch(e => setPageError(formatApiError(e)));
    },
    [buildParams, filters.debouncedSearch],
  );

  useEffect(() => {
    setPage(0);
    void load(undefined, 0);
  }, [
    filters.filterAirline,
    filters.filterDate,
    filters.filterDestination,
    filters.filterOrigin,
    filters.filterStatus,
    filters.debouncedSearch,
    filters.sortOrder,
    load,
  ]);

  useEffect(() => {
    if (page > 0) {
      void load(undefined, page);
    }
  }, [page, load]);

  return {
    flights,
    setFlights,
    page,
    setPage,
    totalPages,
    totalElements,
    pageSize: FLIGHTS_PAGE_SIZE,
    pageError,
    setPageError,
    load,
  };
}
