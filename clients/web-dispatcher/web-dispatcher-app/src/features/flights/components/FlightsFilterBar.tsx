import PaginationBar from '../../../components/PaginationBar';
import type { AirlineRs, FlightRs } from '../../../types';
import { getAirportTimezone, todayAirportDate } from '../../../utils/airportTime';
import { FILTER_STATUSES } from '../constants';
import { FlightsTable } from './FlightsTable';

export interface FlightsFilterBarProps {
  filterDate: string;
  onFilterDateChange: (value: string) => void;
  filterStatus: string;
  onFilterStatusChange: (value: string) => void;
  filterAirline: string;
  onFilterAirlineChange: (value: string) => void;
  filterOrigin: string;
  onFilterOriginChange: (value: string) => void;
  filterDestination: string;
  onFilterDestinationChange: (value: string) => void;
  searchQuery: string;
  onSearchQueryChange: (value: string) => void;
  sortOrder: 'asc' | 'desc';
  onSortOrderToggle: () => void;
  airlines: AirlineRs[];
  exportInfo: string | null;
  onExport: (format: 'pdf' | 'excel') => void;
  onOpenCreate: () => void;
  onOpenGenerate: () => void;
  flights: FlightRs[];
  editingId: number | null;
  highlightIds: Set<number>;
  onStartEdit: (flight: FlightRs) => void;
  onRemoveFlight: (id: number) => void;
  page: number;
  totalPages: number;
  totalElements: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}

/** Toolbar фильтров, экспорт и таблица рейсов с pagination. */
export function FlightsFilterBar({
  filterDate,
  onFilterDateChange,
  filterStatus,
  onFilterStatusChange,
  filterAirline,
  onFilterAirlineChange,
  filterOrigin,
  onFilterOriginChange,
  filterDestination,
  onFilterDestinationChange,
  searchQuery,
  onSearchQueryChange,
  sortOrder,
  onSortOrderToggle,
  airlines,
  exportInfo,
  onExport,
  onOpenCreate,
  onOpenGenerate,
  flights,
  editingId,
  highlightIds,
  onStartEdit,
  onRemoveFlight,
  page,
  totalPages,
  totalElements,
  pageSize,
  onPageChange,
}: FlightsFilterBarProps) {
  const tz = getAirportTimezone();
  return (
    <div className="flights-list">
      <div className="form-row form-row--toolbar flights-toolbar">
        <input
          type="date"
          value={filterDate}
          title={`День планового вылета (${tz})`}
          onChange={e => onFilterDateChange(e.target.value)}
        />
        <button
          type="button"
          className="btn-ghost btn-sm"
          title="Сбросить фильтр по дате"
          onClick={() => onFilterDateChange('')}
        >
          Все даты
        </button>
        <button type="button" className="btn-ghost btn-sm" onClick={() => onFilterDateChange(todayAirportDate())}>
          Сегодня
        </button>
        <button
          type="button"
          className="btn-ghost btn-sm"
          title="Экспорт расписания на выбранный день (PDF)"
          onClick={() => onExport('pdf')}
        >
          PDF
        </button>
        <button
          type="button"
          className="btn-ghost btn-sm"
          title="Экспорт расписания на выбранный день (Excel)"
          onClick={() => onExport('excel')}
        >
          Excel
        </button>
        <select value={filterStatus} onChange={e => onFilterStatusChange(e.target.value)}>
          <option value="">Все статусы</option>
          {FILTER_STATUSES.map(status => (
            <option key={status} value={status}>{status}</option>
          ))}
        </select>
        <select value={filterAirline} onChange={e => onFilterAirlineChange(e.target.value)}>
          <option value="">Все авиакомпании</option>
          {airlines.map(airline => (
            <option key={airline.airlineId} value={String(airline.airlineId)}>
              {airline.iataCode} - {airline.name}
            </option>
          ))}
        </select>
        <input
          className="flights-toolbar__iata"
          placeholder="Откуда (IATA)"
          maxLength={3}
          value={filterOrigin}
          onChange={e => onFilterOriginChange(e.target.value.toUpperCase())}
        />
        <input
          className="flights-toolbar__iata"
          placeholder="Куда (IATA)"
          maxLength={3}
          value={filterDestination}
          onChange={e => onFilterDestinationChange(e.target.value.toUpperCase())}
        />
        <input
          placeholder="Поиск по номеру"
          value={searchQuery}
          onChange={e => onSearchQueryChange(e.target.value.toUpperCase())}
        />
        <button type="button" className="btn-primary btn-sm" onClick={onOpenCreate}>
          Добавить новый рейс
        </button>
        <button type="button" className="btn-ghost btn-sm" onClick={onOpenGenerate}>
          Сгенерировать рейсы
        </button>
        <p className="filter-date-hint">
          {filterDate
            ? `Показаны рейсы с плановым вылетом ${filterDate} (MSK). Экспорт PDF/Excel — на эту дату.`
            : 'Все даты: без фильтра по дню. Экспорт — на сегодня (MSK), либо выберите дату.'}
        </p>
        {exportInfo && <p className="filter-date-hint">{exportInfo}</p>}
      </div>

      <PaginationBar
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={pageSize}
        onPageChange={onPageChange}
      />

      <FlightsTable
        flights={flights}
        editingId={editingId}
        highlightIds={highlightIds}
        sortOrder={sortOrder}
        onSortOrderToggle={onSortOrderToggle}
        onStartEdit={onStartEdit}
        onRemoveFlight={onRemoveFlight}
      />

      <PaginationBar
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={pageSize}
        onPageChange={onPageChange}
      />
    </div>
  );
}
