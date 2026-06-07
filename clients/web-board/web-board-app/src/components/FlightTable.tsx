import type { FlightRs } from '../types';
import { formatAirportTime, formatCompactDate } from '../utils/airportTime';
import { StatusBadge } from './StatusBadge';

interface Props {
  flights: FlightRs[];
  highlightIds: Set<number>;
}

export function FlightTable({ flights, highlightIds }: Props) {
  if (flights.length === 0) {
    return <p className="no-flights">Рейсов нет</p>;
  }

  return (
    <table className="flight-table">
      <thead>
        <tr>
          <th>Дата</th>
          <th>Рейс</th>
          <th>Откуда</th>
          <th>Куда</th>
          <th>Вылет (план)</th>
          <th>Прилёт (план)</th>
          <th>Вылет (факт)</th>
          <th>Прилёт (факт)</th>
          <th>Статус</th>
          <th>Гейт</th>
        </tr>
      </thead>
      <tbody>
        {flights.map(f => {
          const schedule = f.schedule;
          if (!schedule) {
            return (
              <tr
                key={f.flightId}
                className={highlightIds.has(f.flightId) ? 'row-highlight' : ''}
              >
                <td>—</td>
                <td className="cell-flight">—</td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td>—</td>
                <td>{formatAirportTime(f.actualDeparture)}</td>
                <td>{formatAirportTime(f.actualArrival)}</td>
                <td><StatusBadge status={f.status} /></td>
                <td className="cell-gate">—</td>
              </tr>
            );
          }

          const gate = f.currentGateAssignment?.gate;
          const gateLabel = gate
            ? gate.terminal
              ? `${gate.gateNumber} (${gate.terminal})`
              : gate.gateNumber
            : '—';

          return (
            <tr
              key={f.flightId}
              className={highlightIds.has(f.flightId) ? 'row-highlight' : ''}
            >
              <td className="cell-date">{formatCompactDate(f.operationDate ?? f.scheduledDeparture)}</td>
              <td className="cell-flight">{schedule.flightNumber}</td>
              <td>{schedule.originAirport.trim()}</td>
              <td>{schedule.destinationAirport.trim()}</td>
              <td>{formatAirportTime(f.scheduledDeparture)}</td>
              <td>{formatAirportTime(f.scheduledArrival)}</td>
              <td>{formatAirportTime(f.actualDeparture)}</td>
              <td>{formatAirportTime(f.actualArrival)}</td>
              <td><StatusBadge status={f.status} /></td>
              <td className="cell-gate">{gateLabel}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
