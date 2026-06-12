import type { ScheduleRs } from '../../../types';
import { todayAirportDate } from '../../../utils/airportTime';

export interface FlightGenerateModalProps {
  open: boolean;
  generateFrom: string;
  onGenerateFromChange: (value: string) => void;
  generateTo: string;
  onGenerateToChange: (value: string) => void;
  generateScheduleId: string;
  onGenerateScheduleIdChange: (value: string) => void;
  generateInfo: string | null;
  generateSaving: boolean;
  bulkDeleteSaving: boolean;
  schedules: ScheduleRs[];
  onClose: () => void;
  onSubmit: () => void;
  onBulkDeleteBySchedule: () => void;
}

/** Модалка пакетной генерации рейсов из шаблонов. */
export function FlightGenerateModal({
  open,
  generateFrom,
  onGenerateFromChange,
  generateTo,
  onGenerateToChange,
  generateScheduleId,
  onGenerateScheduleIdChange,
  generateInfo,
  generateSaving,
  bulkDeleteSaving,
  schedules,
  onClose,
  onSubmit,
  onBulkDeleteBySchedule,
}: FlightGenerateModalProps) {
  if (!open) return null;
  const today = todayAirportDate();
  const busy = generateSaving || bulkDeleteSaving;

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" aria-labelledby="flight-generate-title">
      <div className="modal-card">
        <h3 id="flight-generate-title">Генерация рейсов</h3>
        <p className="modal-hint">
          Создаёт рейсы из активных шаблонов за указанный период. Список рейсов фильтруется по одному
          дню — после успешной генерации фильтр сбрасывается автоматически.
        </p>
        {generateInfo && <p className="modal-alert" role="status">{generateInfo}</p>}
        <label>
          С
          <input
            type="date"
            value={generateFrom}
            min={today}
            onChange={e => onGenerateFromChange(e.target.value)}
          />
        </label>
        <label>
          По
          <input
            type="date"
            value={generateTo}
            min={generateFrom || today}
            onChange={e => onGenerateToChange(e.target.value)}
          />
        </label>
        <label>
          Шаблон (необязательно)
          <select value={generateScheduleId} onChange={e => onGenerateScheduleIdChange(e.target.value)}>
            <option value="">— все активные шаблоны —</option>
            {schedules.filter(s => s.isActive !== false).map(s => (
              <option key={s.scheduleId} value={String(s.scheduleId)}>
                {s.flightNumber} {s.originAirport.trim()}→{s.destinationAirport.trim()}
              </option>
            ))}
          </select>
        </label>
        <div className="modal-actions modal-actions--center">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={busy}>
            Закрыть
          </button>
          <button
            type="button"
            className="btn-danger"
            disabled={busy || !generateScheduleId}
            onClick={onBulkDeleteBySchedule}
            title="Удаляет только рейсы выбранного шаблона"
          >
            {bulkDeleteSaving ? 'Удаление…' : 'Удалить рейсы шаблона'}
          </button>
          <button type="button" className="btn-primary" disabled={busy} onClick={onSubmit}>
            {generateSaving ? 'Генерация…' : 'Сгенерировать'}
          </button>
        </div>
      </div>
    </div>
  );
}
