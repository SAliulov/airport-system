import { useAirportConfig } from '../../../../../../shared/context/AirportConfigProvider';
import type { AirlineRs, PeriodicityType } from '../../../types';
import type { ScheduleFormValues, ScheduleSlotFormValues } from '../../../utils/fieldValidation';

const DOW_OPTIONS = [
  { value: '1', label: 'Пн' },
  { value: '2', label: 'Вт' },
  { value: '3', label: 'Ср' },
  { value: '4', label: 'Чт' },
  { value: '5', label: 'Пт' },
  { value: '6', label: 'Сб' },
  { value: '7', label: 'Вс' },
];

export interface ScheduleFormModalProps {
  open: boolean;
  title: string;
  titleId: string;
  modalError: string | null;
  form: ScheduleFormValues;
  fieldErrors: Record<string, string>;
  airlines: AirlineRs[];
  saving: boolean;
  submitLabel: string;
  pendingLabel: string;
  onClose: () => void;
  onSubmit: () => void;
  onFormChange: (updater: (prev: ScheduleFormValues) => ScheduleFormValues) => void;
  onClearFieldError: (key: string) => void;
  onPeriodicityChange: (type: PeriodicityType) => void;
  onUpdateSlot: (index: number, patch: Partial<ScheduleSlotFormValues>) => void;
  onAddSlot: () => void;
  onRemoveSlot: (index: number) => void;
  dismissOnOverlayClick?: boolean;
}

function invalidClass(fieldErrors: Record<string, string>, field: string) {
  return fieldErrors[field] ? 'field-invalid' : '';
}

/** Модальная форма создания/редактирования расписания. */
export function ScheduleFormModal({
  open,
  title,
  titleId,
  modalError,
  form,
  fieldErrors,
  airlines,
  saving,
  submitLabel,
  pendingLabel,
  onClose,
  onSubmit,
  onFormChange,
  onClearFieldError,
  onPeriodicityChange,
  onUpdateSlot,
  onAddSlot,
  onRemoveSlot,
  dismissOnOverlayClick = false,
}: ScheduleFormModalProps) {
  const { homeIata } = useAirportConfig();
  if (!open) return null;

  const ic = (field: string) => invalidClass(fieldErrors, field);

  return (
    <div
      className="modal-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby={titleId}
      onClick={dismissOnOverlayClick ? e => { if (e.target === e.currentTarget) onClose(); } : undefined}
    >
      <div className="modal-card modal-card--form" onClick={e => e.stopPropagation()}>
        <h3 id={titleId}>{title}</h3>
        {modalError && <p className="modal-alert" role="alert">{modalError}</p>}

        <div className="modal-form-grid">
          <div className="modal-field">
            <span className="modal-field__label">Номер рейса</span>
            <input
              className={ic('flightNumber')}
              placeholder="SU1234"
              maxLength={20}
              value={form.flightNumber}
              onChange={e => {
                onClearFieldError('flightNumber');
                onFormChange(p => ({ ...p, flightNumber: e.target.value.toUpperCase() }));
              }}
            />
            {fieldErrors.flightNumber && (
              <span className="modal-field__error">{fieldErrors.flightNumber}</span>
            )}
          </div>

          <div className="modal-form-row">
            <div className="modal-field">
              <span className="modal-field__label">Откуда (IATA)</span>
              <input
                className={ic('originAirport')}
                maxLength={3}
                placeholder={homeIata}
                value={form.originAirport}
                onChange={e => {
                  onClearFieldError('originAirport');
                  onClearFieldError('destinationAirport');
                  onFormChange(p => ({ ...p, originAirport: e.target.value.toUpperCase() }));
                }}
              />
              {fieldErrors.originAirport && (
                <span className="modal-field__error">{fieldErrors.originAirport}</span>
              )}
            </div>
            <div className="modal-field">
              <span className="modal-field__label">Куда (IATA)</span>
              <input
                className={ic('destinationAirport')}
                maxLength={3}
                placeholder="LED"
                value={form.destinationAirport}
                onChange={e => {
                  onClearFieldError('destinationAirport');
                  onClearFieldError('originAirport');
                  onFormChange(p => ({ ...p, destinationAirport: e.target.value.toUpperCase() }));
                }}
              />
              {fieldErrors.destinationAirport && (
                <span className="modal-field__error">{fieldErrors.destinationAirport}</span>
              )}
            </div>
          </div>

          <div className="modal-form-row">
            <div className="modal-field">
              <span className="modal-field__label">Действует с</span>
              <input
                className={ic('effectiveFrom')}
                type="date"
                value={form.effectiveFrom}
                onChange={e => {
                  onClearFieldError('effectiveFrom');
                  onClearFieldError('effectiveTo');
                  onFormChange(p => ({ ...p, effectiveFrom: e.target.value }));
                }}
              />
              {fieldErrors.effectiveFrom && (
                <span className="modal-field__error">{fieldErrors.effectiveFrom}</span>
              )}
            </div>
            <div className="modal-field">
              <span className="modal-field__label">Действует по</span>
              <input
                className={ic('effectiveTo')}
                type="date"
                value={form.effectiveTo}
                onChange={e => {
                  onClearFieldError('effectiveTo');
                  onFormChange(p => ({ ...p, effectiveTo: e.target.value }));
                }}
              />
              {fieldErrors.effectiveTo && (
                <span className="modal-field__error">{fieldErrors.effectiveTo}</span>
              )}
            </div>
          </div>

          <div className="modal-form-row">
            <div className="modal-field">
              <span className="modal-field__label">Периодичность</span>
              <p className="modal-hint">
                INTERVAL — один слот в день по шагу; два рейса в день — два шаблона с разными номерами.
              </p>
              <select
                className={ic('periodicityType')}
                value={form.periodicityType}
                onChange={e => onPeriodicityChange(e.target.value as PeriodicityType)}
              >
                <option value="WEEKLY">WEEKLY — по дням недели</option>
                <option value="INTERVAL">INTERVAL — через N дней</option>
              </select>
            </div>
            <div className="modal-field">
              <span className="modal-field__label">Шаг</span>
              <input
                className={ic('periodicityStep')}
                type="number"
                min={1}
                value={form.periodicityStep}
                onChange={e => {
                  onClearFieldError('periodicityStep');
                  onFormChange(p => ({ ...p, periodicityStep: e.target.value }));
                }}
              />
              {fieldErrors.periodicityStep && (
                <span className="modal-field__error">{fieldErrors.periodicityStep}</span>
              )}
            </div>
          </div>

          <div className="modal-field">
            <label className="modal-field__checkbox">
              <input
                type="checkbox"
                checked={form.isActive}
                onChange={e => onFormChange(p => ({ ...p, isActive: e.target.checked }))}
              />
              <span className="modal-field__checkbox-text">
                Активный шаблон
                <p className="modal-field__checkbox-hint">
                  Нельзя снять активность, если по шаблону уже созданы рейсы — сначала удалите рейсы.
                </p>
              </span>
            </label>
          </div>

          <div className="modal-field">
            <span className="modal-field__label">Слоты</span>
            <p className="modal-hint">
              День недели слота должен попадать в период действия шаблона — иначе при сохранении будет ошибка.
            </p>
            {form.slots.map((slot, index) => (
              <div key={index} className="modal-form-row modal-form-row--slot">
                {form.periodicityType === 'WEEKLY' && (
                  <select
                    className={ic('slots')}
                    value={slot.dayOfWeek}
                    onChange={e => onUpdateSlot(index, { dayOfWeek: e.target.value })}
                  >
                    {DOW_OPTIONS.map(o => (
                      <option key={o.value} value={o.value}>{o.label}</option>
                    ))}
                  </select>
                )}
                <input
                  className={ic('slots')}
                  type="time"
                  value={slot.departureTime}
                  title="Вылет"
                  onChange={e => onUpdateSlot(index, { departureTime: e.target.value })}
                />
                <input
                  className={ic('slots')}
                  type="time"
                  value={slot.arrivalTime}
                  title="Прилёт"
                  onChange={e => onUpdateSlot(index, { arrivalTime: e.target.value })}
                />
                {form.periodicityType === 'WEEKLY' && form.slots.length > 1 && (
                  <button type="button" className="btn-ghost btn-sm" onClick={() => onRemoveSlot(index)}>
                    ✕
                  </button>
                )}
              </div>
            ))}
            {form.periodicityType === 'WEEKLY' && (
              <button type="button" className="btn-ghost btn-sm" onClick={onAddSlot}>
                + слот
              </button>
            )}
            {fieldErrors.slots && (
              <span className="modal-field__error">{fieldErrors.slots}</span>
            )}
          </div>

          <div className="modal-field">
            <span className="modal-field__label">Авиакомпания</span>
            <select
              className={ic('airlineId')}
              value={form.airlineId}
              onChange={e => {
                onClearFieldError('airlineId');
                onFormChange(p => ({ ...p, airlineId: e.target.value }));
              }}
            >
              <option value="">— выберите —</option>
              {airlines.map(a => (
                <option key={a.airlineId} value={String(a.airlineId)}>
                  {a.iataCode} — {a.name}
                </option>
              ))}
            </select>
            {fieldErrors.airlineId && (
              <span className="modal-field__error">{fieldErrors.airlineId}</span>
            )}
          </div>
        </div>

        <div className="modal-actions modal-actions--center">
          <button type="button" className="btn-ghost" onClick={onClose} disabled={saving}>
            Отмена
          </button>
          <button type="button" className="btn-primary" disabled={saving} onClick={onSubmit}>
            {saving ? pendingLabel : submitLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
