import type { AirlineRs } from '../types';

export type DatePreset = 'today' | 'all' | '7d' | '30d';

interface Props {
  date: string;
  datePreset: DatePreset;
  origin: string;
  destination: string;
  airlineId: string;
  airlines: AirlineRs[];
  onDatePresetChange: (preset: DatePreset) => void;
  onDateChange: (value: string) => void;
  onOriginChange: (value: string) => void;
  onDestinationChange: (value: string) => void;
  onAirlineChange: (value: string) => void;
}

const PRESETS: { id: DatePreset; label: string }[] = [
  { id: 'today', label: 'Сегодня' },
  { id: 'all', label: 'Все даты' },
  { id: '7d', label: '7 дней' },
  { id: '30d', label: '30 дней' },
];

export function FilterBar({
  date,
  datePreset,
  origin,
  destination,
  airlineId,
  airlines,
  onDatePresetChange,
  onDateChange,
  onOriginChange,
  onDestinationChange,
  onAirlineChange,
}: Props) {
  return (
    <div className="filter-bar">
      <div className="filter-bar__presets">
        <span className="filter-bar__group-label">Период</span>
        <div className="filter-bar__preset-buttons">
          {PRESETS.map(p => (
            <button
              key={p.id}
              type="button"
              className={datePreset === p.id ? 'preset-btn preset-btn--active' : 'preset-btn'}
              onClick={() => onDatePresetChange(p.id)}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>
      <label>
        <span>Дата (точная)</span>
        <input
          type="date"
          value={date}
          disabled={datePreset === '7d' || datePreset === '30d'}
          onChange={e => {
            onDateChange(e.target.value);
            if (e.target.value) onDatePresetChange('today');
          }}
        />
      </label>
      <label>
        <span>Откуда (IATA)</span>
        <input
          type="text"
          maxLength={3}
          placeholder="SVO"
          value={origin}
          onChange={e => onOriginChange(e.target.value.toUpperCase())}
        />
      </label>
      <label>
        <span>Куда (IATA)</span>
        <input
          type="text"
          maxLength={3}
          placeholder="LED"
          value={destination}
          onChange={e => onDestinationChange(e.target.value.toUpperCase())}
        />
      </label>
      <label>
        <span>Авиакомпания</span>
        <select value={airlineId} onChange={e => onAirlineChange(e.target.value)}>
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
