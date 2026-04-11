import type { AirlineRs } from '../types';

interface Props {
  date: string;
  direction: string;
  airlineId: string;
  airlines: AirlineRs[];
  onChange: (field: 'date' | 'direction' | 'airlineId', value: string) => void;
}

export function FilterBar({ date, direction, airlineId, airlines, onChange }: Props) {
  return (
    <div className="filter-bar">
      <label>
        <span>Дата</span>
        <input
          type="date"
          value={date}
          onChange={e => onChange('date', e.target.value)}
        />
      </label>
      <label>
        <span>Направление (IATA)</span>
        <input
          type="text"
          maxLength={3}
          placeholder="SVO"
          value={direction}
          onChange={e => onChange('direction', e.target.value.toUpperCase())}
        />
      </label>
      <label>
        <span>Авиакомпания</span>
        <select value={airlineId} onChange={e => onChange('airlineId', e.target.value)}>
          <option value="">Все</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>
              {a.iataCode} — {a.name}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
