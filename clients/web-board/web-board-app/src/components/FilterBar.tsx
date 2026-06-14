import type { AirlineRs, FlightStatus } from '../types';
import {
  DAY_PRESETS,
  FILTER_STATUSES,
  STATUS_LABELS,
  TIME_SLOTS,
  type BoardFilterState,
} from '../constants/boardFilters';

interface Props {
  filters: BoardFilterState;
  airlines: AirlineRs[];
  terminals: string[];
  homeIata: string;
  onChange: (patch: Partial<BoardFilterState>) => void;
}

function isFlightStatus(value: string): value is FlightStatus {
  return (FILTER_STATUSES as string[]).includes(value);
}

/** Панель фильтров табло. */
export function FilterBar({ filters, airlines, terminals, homeIata, onChange }: Props) {
  const iataHint =
    (filters.origin.length > 0 && filters.origin.length !== 3)
    || (filters.destination.length > 0 && filters.destination.length !== 3)
      ? 'Для фильтра по маршруту укажите 3 буквы IATA'
      : null;

  return (
    <div className="filter-bar">
      <div className="filter-bar__presets">
        <span className="filter-bar__group-label">День</span>
        <div className="filter-bar__preset-buttons">
          {DAY_PRESETS.map(p => (
            <button
              key={p.id}
              type="button"
              className={filters.dayPreset === p.id ? 'preset-btn preset-btn--active' : 'preset-btn'}
              onClick={() => onChange({ dayPreset: p.id })}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>
      <label>
        <span>Время (MSK)</span>
        <select
          value={filters.hourFrom ?? ''}
          onChange={e => {
            const v = e.target.value;
            onChange({ hourFrom: v === '' ? null : Number(v) });
          }}
        >
          {TIME_SLOTS.map(slot => (
            <option key={slot.label} value={slot.hourFrom ?? ''}>
              {slot.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Терминал</span>
        <select value={filters.terminal} onChange={e => onChange({ terminal: e.target.value })}>
          <option value="">Все терминалы</option>
          {terminals.map(t => (
            <option key={t} value={t}>
              Терминал {t}
            </option>
          ))}
        </select>
      </label>
      <label>
        <span>Поиск по номеру</span>
        <input
          type="search"
          placeholder="SU100…"
          value={filters.search}
          onChange={e => onChange({ search: e.target.value.toUpperCase() })}
        />
      </label>
      <label>
        <span>Статус</span>
        <select
          value={filters.status}
          onChange={e => {
            const v = e.target.value;
            onChange({ status: v === '' || isFlightStatus(v) ? v : '' });
          }}
        >
          <option value="">Все</option>
          {FILTER_STATUSES.map(s => (
            <option key={s} value={s}>{STATUS_LABELS[s]}</option>
          ))}
        </select>
      </label>
      <label>
        <span>Откуда (IATA)</span>
        <input
          type="text"
          maxLength={3}
          placeholder={homeIata}
          value={filters.origin}
          onChange={e => onChange({ origin: e.target.value.toUpperCase() })}
        />
      </label>
      <label>
        <span>Куда (IATA)</span>
        <input
          type="text"
          maxLength={3}
          placeholder="LED"
          value={filters.destination}
          onChange={e => onChange({ destination: e.target.value.toUpperCase() })}
        />
      </label>
      <label>
        <span>Авиакомпания</span>
        <select value={filters.airlineId} onChange={e => onChange({ airlineId: e.target.value })}>
          <option value="">Все</option>
          {airlines.map(a => (
            <option key={a.airlineId} value={String(a.airlineId)}>
              {a.iataCode} — {a.name}
            </option>
          ))}
        </select>
      </label>
      {iataHint && <p className="filter-bar__hint">{iataHint}</p>}
    </div>
  );
}
