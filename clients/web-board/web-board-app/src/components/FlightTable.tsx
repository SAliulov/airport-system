import type { FlightRs } from '../types';
import { StatusBadge } from './StatusBadge';


function fmtTime(iso?: string) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
}

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
          <th>Рейс</th>
          <th>Откуда</th>
          <th>Куда</th>
          <th>Вылет (план)</th>
          <th>Прилёт (план)</th>
          <th>Вылет (факт)</th>
          <th>Статус</th>
          <th>Гейт</th>
        </tr>
      </thead>
      <tbody>
        {flights.map(f => {
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
              <td className="cell-flight">{f.schedule.flightNumber}</td>
              <td>{f.schedule.originAirport.trim()}</td>
              <td>{f.schedule.destinationAirport.trim()}</td>
              <td>{fmtTime(f.schedule.scheduledDeparture)}</td>
              <td>{fmtTime(f.schedule.scheduledArrival)}</td>
              <td>{fmtTime(f.actualDeparture)}</td>
              <td><StatusBadge status={f.status} /></td>
              <td className="cell-gate">{gateLabel}</td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
