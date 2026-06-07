import type { FlightRs } from '../../../types';
import { formatGateInterval, formatGateLabel } from '../domain/flightFormatters';

/** Ячейка таблицы «Гейт» с tooltip интервала. */
export function GateTableCell({ flight }: { flight: FlightRs }) {
  const assignment = flight.currentGateAssignment;
  const g = assignment?.gate;
  if (!g?.gateNumber) return <td>Не назначен</td>;
  const interval = formatGateInterval(assignment);
  return (
    <td className="gate-cell" title={interval ?? undefined}>
      {formatGateLabel(g)}
    </td>
  );
}
