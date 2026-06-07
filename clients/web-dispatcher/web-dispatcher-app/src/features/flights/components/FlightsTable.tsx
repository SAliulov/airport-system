import type { FlightRs } from '../../../types';
import { formatAirportDateTime } from '../../../utils/airportTime';
import { formatAircraftCell } from '../domain/flightFormatters';
import { flightStatusColor } from '../domain/flightStatusTheme';
import { GateTableCell } from './GateTableCell';

export interface FlightsTableProps {
  flights: FlightRs[];
  editingId: number | null;
  highlightIds: Set<number>;
  sortOrder: 'asc' | 'desc';
  onSortOrderToggle: () => void;
  onStartEdit: (flight: FlightRs) => void;
  onRemoveFlight: (id: number) => void;
}

/** Таблица операционных рейсов. */
export function FlightsTable({
  flights,
  editingId,
  highlightIds,
  sortOrder,
  onSortOrderToggle,
  onStartEdit,
  onRemoveFlight,
}: FlightsTableProps) {
  return (
    <table className="data-table flights-table">
      <thead>
        <tr>
          <th>Рейс</th>
          <th>Откуда</th>
          <th>Куда</th>
          <th
            className="sortable-th"
            title="Сортировка по плановому вылету"
            onClick={onSortOrderToggle}
          >
            План вылет {sortOrder === 'asc' ? '↑' : '↓'}
          </th>
          <th>План прилёт</th>
          <th>Авиакомпания</th>
          <th>Факт вылет</th>
          <th>Факт прилёт</th>
          <th>Статус</th>
          <th>Тип ВС</th>
          <th>Гейт</th>
          <th />
        </tr>
      </thead>
      <tbody>
        {flights.map(flight => (
          <tr
            key={flight.flightId}
            className={[
              editingId === flight.flightId ? 'row-selected' : '',
              highlightIds.has(flight.flightId) ? 'row-highlight' : '',
            ].filter(Boolean).join(' ')}
          >
            <td>{flight.schedule?.flightNumber ?? '—'}</td>
            <td>{(flight.schedule?.originAirport ?? '').trim() || '—'}</td>
            <td>{(flight.schedule?.destinationAirport ?? '').trim() || '—'}</td>
            <td>{formatAirportDateTime(flight.scheduledDeparture)}</td>
            <td>{formatAirportDateTime(flight.scheduledArrival)}</td>
            <td>{flight.schedule?.airline?.name ?? '—'}</td>
            <td>{formatAirportDateTime(flight.actualDeparture)}</td>
            <td>{formatAirportDateTime(flight.actualArrival)}</td>
            <td>
              <span
                className="flight-status-label"
                style={{ color: flightStatusColor(flight.status) }}
              >
                {flight.status}
              </span>
            </td>
            <td>{formatAircraftCell(flight)}</td>
            <GateTableCell flight={flight} />
            <td className="cell-actions">
              <button
                type="button"
                className="btn-ghost btn-sm"
                title={
                  flight.status === 'ARRIVED'
                    ? 'Скорректировать фактические времена'
                    : 'Редактировать'
                }
                onClick={() => onStartEdit(flight)}
                aria-label="Редактировать"
              >
                ✏
              </button>
              <button
                type="button"
                className="btn-danger btn-sm"
                title="Удалить рейс"
                onClick={() => onRemoveFlight(flight.flightId)}
              >
                ✕
              </button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
