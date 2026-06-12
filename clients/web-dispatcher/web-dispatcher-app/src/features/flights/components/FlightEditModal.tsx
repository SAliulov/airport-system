import type { AircraftTypeRs, FlightRs, GateRs, SizeCategory } from '../../../types';
import { useAirportConfig } from '../../../../../../shared/context/AirportConfigProvider';
import { toDatetimeLocalValue } from '../../../utils/airportTime';
import {
  allowedStatusOptions,
  flightDirection,
  isAircraftFitsGate,
  isClosedFlightStatus,
  isGateIntervalDamaged,
  isGateMutable,
  type GateFormValues,
} from '../domain/flightRules';
import {
  flightScheduleLabel,
  formatGateInterval,
  formatGateLabel,
  gateOptionLabel,
} from '../domain/flightFormatters';
import { flightStatusColor } from '../domain/flightStatusTheme';
import type { DelayFormValues } from '../hooks/useFlightEdit';

export interface FlightEditModalProps {
  editingId: number;
  editDetail: FlightRs;
  modalError: string | null;
  actualTimesOnly: boolean;
  statusNew: string;
  onStatusNewChange: (value: string) => void;
  statusActualDeparture: string;
  onStatusActualDepartureChange: (value: string) => void;
  statusActualArrival: string;
  onStatusActualArrivalChange: (value: string) => void;
  acType: string;
  onAcTypeChange: (value: string) => void;
  gateForm: GateFormValues;
  onGateFormChange: (updater: (prev: GateFormValues) => GateFormValues) => void;
  delayForm: DelayFormValues;
  onDelayFormChange: (updater: (prev: DelayFormValues) => DelayFormValues) => void;
  aircraftTypes: AircraftTypeRs[];
  gates: GateRs[];
  selectedAircraftSize?: SizeCategory;
  selectedGateMax?: SizeCategory;
  editSaving: boolean;
  onClose: () => void;
  onApply: () => void;
}

/** Модалка редактирования рейса: статус, ВС, гейт, задержка. */
export function FlightEditModal({
  editingId,
  editDetail,
  modalError,
  actualTimesOnly,
  statusNew,
  onStatusNewChange,
  statusActualDeparture,
  onStatusActualDepartureChange,
  statusActualArrival,
  onStatusActualArrivalChange,
  acType,
  onAcTypeChange,
  gateForm,
  onGateFormChange,
  delayForm,
  onDelayFormChange,
  aircraftTypes,
  gates,
  selectedAircraftSize,
  selectedGateMax,
  editSaving,
  onClose,
  onApply,
}: FlightEditModalProps) {
  const { homeIata, timezone } = useAirportConfig();
  const resourceLocks = isClosedFlightStatus(editDetail.status);
  const gateLocks = !isGateMutable(editDetail.status, editDetail.schedule);
  const gateIntervalDamaged = isGateIntervalDamaged(editDetail.currentGateAssignment);
  const gateFromFallback = toDatetimeLocalValue(editDetail.scheduledDeparture);
  const gateToFallback = toDatetimeLocalValue(editDetail.scheduledArrival);
  const showDelayFields = statusNew === 'DELAYED' || (editDetail.status === 'DELAYED' && !statusNew);

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="flight-edit-title"
      onClick={e => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="modal-card modal-card--edit" onClick={e => e.stopPropagation()}>
        <h3 id="flight-edit-title">
          {actualTimesOnly ? 'Коррекция времени' : 'Редактирование'} рейса #{editingId} — {editDetail.schedule?.flightNumber ?? '—'}
        </h3>
        {modalError && <p className="modal-alert" role="alert">{modalError}</p>}
        <p className="modal-hint">
          {actualTimesOnly
            ? 'Исправьте фактическое время вылета и/или прилёта. Остальные параметры рейса закрыты.'
            : 'Заполните нужные поля и нажмите «Применить изменения».'}
        </p>
        <div className="modal-card__body">
          <section className="panel-section panel-section--bordered">
            <h3 className="panel-section__title">Расписание</h3>
            <p className="modal-readonly">{flightScheduleLabel(editDetail)}</p>
            <p className="modal-hint">Слот расписания нельзя изменить — создайте новый рейс при необходимости.</p>
          </section>

          {actualTimesOnly ? (
            <section className="panel-section">
              <h3 className="panel-section__title">Фактические времена</h3>
              <label className="field-block">
                Фактическое время вылета
                <input
                  type="datetime-local"
                  className="field-block__input"
                  value={statusActualDeparture}
                  onChange={e => onStatusActualDepartureChange(e.target.value)}
                />
              </label>
              <label className="field-block">
                Фактическое время прилёта
                <input
                  type="datetime-local"
                  className="field-block__input"
                  value={statusActualArrival}
                  onChange={e => onStatusActualArrivalChange(e.target.value)}
                />
              </label>
              <p className="panel-section__status-line">
                Статус:{' '}
                <strong style={{ color: flightStatusColor(editDetail.status) }}>{editDetail.status}</strong>
              </p>
            </section>
          ) : (
            <>
              <section className="panel-section panel-section--bordered">
                <h3 className="panel-section__title">Статус</h3>
                <select
                  className="field-block__input"
                  value={statusNew}
                  onChange={e => onStatusNewChange(e.target.value)}
                >
                  <option value="">— не менять —</option>
                  {allowedStatusOptions(editDetail).map(status => (
                    <option key={status} value={status}>{status}</option>
                  ))}
                </select>
                <p className="modal-hint modal-hint--tight">
                  Вылет/прилёт в {homeIata} — вручную; задержка outbound и автопереходы — планировщик (раз в ~1 мин).
                </p>
                {flightDirection(editDetail.schedule) === 'inbound' && !editDetail.aircraftType && (
                  <p className="modal-hint modal-hint--warning">
                    Без типа ВС планировщик переведёт рейс в DELAYED после планового вылета + 5 мин;
                    автовылет (DEPARTED) — только после назначения типа ВС.
                  </p>
                )}
                {statusNew === 'DEPARTED' && (
                  <label className="actual-time-field field-block">
                    Фактическое время вылета (MSK)
                    <input
                      type="datetime-local"
                      value={statusActualDeparture}
                      onChange={e => onStatusActualDepartureChange(e.target.value)}
                    />
                    <span className="modal-hint modal-hint--tight">{timezone} — не зависит от часового пояса браузера</span>
                  </label>
                )}
                {statusNew === 'ARRIVED' && (
                  <label className="actual-time-field field-block">
                    Фактическое время прилёта (MSK)
                    <input
                      type="datetime-local"
                      value={statusActualArrival}
                      onChange={e => onStatusActualArrivalChange(e.target.value)}
                    />
                    <span className="modal-hint modal-hint--tight">{timezone} — не зависит от часового пояса браузера</span>
                  </label>
                )}
                <p className="panel-section__status-line">
                  Текущий:{' '}
                  <strong style={{ color: flightStatusColor(editDetail.status) }}>{editDetail.status}</strong>
                </p>
              </section>

              <section className="panel-section panel-section--bordered">
                <h3 className="panel-section__title">Тип ВС</h3>
                <select
                  className="field-block__input"
                  value={acType}
                  onChange={e => onAcTypeChange(e.target.value)}
                  disabled={resourceLocks}
                  title={resourceLocks ? 'Ресурсы закрыты для этого статуса рейса' : undefined}
                >
                  <option value="">— не менять —</option>
                  {aircraftTypes.map(type => {
                    const disabled = !isAircraftFitsGate(type.sizeCategory, selectedGateMax);
                    return (
                      <option
                        key={type.aircraftTypeId}
                        value={String(type.aircraftTypeId)}
                        disabled={disabled}
                      >
                        {type.icaoCode} ({type.sizeCategory ?? '?'})
                        {disabled ? ' — не подходит к гейту' : ''}
                      </option>
                    );
                  })}
                </select>
                <p className="panel-section__status-line">
                  Текущий: <strong>{editDetail.aircraftType?.icaoCode ?? '—'}</strong>
                </p>
              </section>

              <section className="panel-section panel-section--bordered">
                <h3 className="panel-section__title">Гейт</h3>
                {editDetail.currentGateAssignment?.gate?.gateId ? (
                  <div className="gate-current-box">
                    <div className="gate-current-box__label">Текущее назначение</div>
                    <strong>{formatGateLabel(editDetail.currentGateAssignment.gate)}</strong>
                    {formatGateInterval(editDetail.currentGateAssignment) && (
                      <div className="gate-current-box__interval">
                        {formatGateInterval(editDetail.currentGateAssignment)}
                      </div>
                    )}
                  </div>
                ) : (
                  <p className="modal-hint">Гейт не назначен</p>
                )}
                <p className="modal-hint modal-hint--tight">
                  {editDetail.currentGateAssignment?.gate?.gateId
                    ? 'Изменить гейт или интервал занятости'
                    : 'Назначить гейт (интервал по умолчанию — плановые вылет/прилёт)'}
                  {flightDirection(editDetail.schedule) === 'inbound' && editDetail.status === 'DEPARTED' && (
                    <span> Inbound DEPARTED: гейт {homeIata} можно назначить или сменить до прилёта.</span>
                  )}
                </p>
                {gateIntervalDamaged && (
                  <p className="modal-hint modal-hint--warning">
                    Интервал в БД повреждён (конец раньше начала). Укажите корректные «С» и «По» и сохраните.
                  </p>
                )}
                <select
                  className="field-block__input"
                  value={gateForm.gateId}
                  disabled={gateLocks}
                  onChange={e => onGateFormChange(prev => ({ ...prev, gateId: e.target.value }))}
                  title={gateLocks ? 'Гейт закрыт для этого статуса рейса' : undefined}
                >
                  <option value="">— не назначать —</option>
                  {gates.filter(gate => gate.isActive).map(gate => {
                    const disabled = !isAircraftFitsGate(selectedAircraftSize, gate.maxSizeCategory);
                    return (
                      <option key={gate.gateId} value={String(gate.gateId)} disabled={disabled}>
                        {gateOptionLabel(gate)}
                        {disabled ? ' — не подходит к типу ВС' : ''}
                      </option>
                    );
                  })}
                </select>
                <p className="modal-hint modal-hint--tight">
                  Интервал занятости гейта (с / по), время аэропорта {homeIata}.
                  При смене гейта предыдущее назначение обрезается по полю «С» нового интервала.
                </p>
                <div className="form-row form-row--gate-interval">
                  <label>
                    С
                    <input
                      type="datetime-local"
                      disabled={gateLocks}
                      value={gateForm.assignedFrom || gateFromFallback}
                      onChange={e => onGateFormChange(prev => ({ ...prev, assignedFrom: e.target.value }))}
                    />
                  </label>
                  <label>
                    По
                    <input
                      type="datetime-local"
                      disabled={gateLocks}
                      value={gateForm.assignedTo || gateToFallback}
                      onChange={e => onGateFormChange(prev => ({ ...prev, assignedTo: e.target.value }))}
                    />
                  </label>
                </div>
              </section>

              {showDelayFields && (
                <section className="panel-section">
                  <h3 className="panel-section__title">Задержка (новое предупреждение)</h3>
                  <p className="modal-hint modal-hint--tight">
                    {statusNew === 'DELAYED'
                      ? 'Укажите минуты и причину — сохранятся вместе с переводом в DELAYED.'
                      : 'Рейс уже DELAYED — можно добавить текст задержки.'}
                  </p>
                  <div className="form-row form-row--delay">
                    <input
                      placeholder="Минуты"
                      type="number"
                      min={1}
                      className="delay-minutes-input"
                      value={delayForm.delayMinutes}
                      onChange={e => onDelayFormChange(prev => ({ ...prev, delayMinutes: e.target.value }))}
                    />
                    <input
                      placeholder="Причина"
                      className="delay-reason-input"
                      value={delayForm.reason}
                      onChange={e => onDelayFormChange(prev => ({ ...prev, reason: e.target.value }))}
                    />
                  </div>
                  {(editDetail.delayWarnings ?? []).length === 0 ? (
                    <p className="muted panel-section__status-line">Нет записей о задержке</p>
                  ) : (
                    (editDetail.delayWarnings ?? []).map(warning => (
                      <div key={warning.warningId} className="delay-row">
                        ! {warning.delayMinutes} мин — {warning.reason ?? 'причина не указана'}
                      </div>
                    ))
                  )}
                </section>
              )}
            </>
          )}
        </div>

        <div className="modal-actions modal-actions--footer">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={editSaving}>
            Закрыть
          </button>
          <button type="button" className="btn-primary" disabled={editSaving} onClick={onApply}>
            {editSaving ? 'Сохранение…' : 'Применить изменения'}
          </button>
        </div>
      </div>
    </div>
  );
}
