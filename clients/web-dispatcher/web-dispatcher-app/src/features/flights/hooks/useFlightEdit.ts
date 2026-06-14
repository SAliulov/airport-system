import { useCallback, useEffect, useRef, useState } from 'react';
import {
  assignAircraft,
  assignGate,
  correctActualTimes,
  createDelayWarning,
  getAircraftTypes,
  getFlightById,
  getGates,
  updateFlightStatus,
} from '../../../services/api';
import { formatApiError } from '../../../utils/apiError';
import { fromDatetimeLocalValue, toDatetimeLocalValue } from '../../../utils/airportTime';
import type { AircraftTypeRs, FlightRs, GateRs } from '../../../types';
import {
  gateAssignmentChanged,
  gateFormFromFlight,
  isActualTimesOnlyMode,
  isClosedFlightStatus,
  isGateMutable,
  type GateFormValues,
} from '../domain/flightRules';

export interface DelayFormValues {
  delayMinutes: string;
  reason: string;
}

export interface UseFlightEditOptions {
  onSaved: () => void;
  setPageError: (msg: string | null) => void;
  setGates: React.Dispatch<React.SetStateAction<GateRs[]>>;
  setAircraftTypes: React.Dispatch<React.SetStateAction<AircraftTypeRs[]>>;
}

/**
 * Состояние и сохранение модалки редактирования рейса (статус, ВС, гейт, задержка).
 */
export function useFlightEdit({ onSaved, setPageError, setGates, setAircraftTypes }: UseFlightEditOptions) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDetail, setEditDetail] = useState<FlightRs | null>(null);
  const [modalError, setModalError] = useState<string | null>(null);
  const [statusNew, setStatusNew] = useState('');
  const [statusActualDeparture, setStatusActualDeparture] = useState('');
  const [statusActualArrival, setStatusActualArrival] = useState('');
  const [acType, setAcType] = useState('');
  const [gateForm, setGateForm] = useState<GateFormValues>({ gateId: '', assignedFrom: '', assignedTo: '' });
  const [delayForm, setDelayForm] = useState<DelayFormValues>({ delayMinutes: '', reason: '' });
  const [editSaving, setEditSaving] = useState(false);
  const editingDetailRef = useRef<FlightRs | null>(null);

  const syncEditFormFromDetail = useCallback((detail: FlightRs, clearPending = true) => {
    setStatusActualDeparture(toDatetimeLocalValue(detail.actualDeparture));
    setStatusActualArrival(toDatetimeLocalValue(detail.actualArrival));
    setAcType(detail.aircraftType ? String(detail.aircraftType.aircraftTypeId) : '');
    setGateForm(gateFormFromFlight(detail));
    if (clearPending) {
      setStatusNew('');
      setDelayForm({ delayMinutes: '', reason: '' });
    }
  }, []);

  useEffect(() => {
    editingDetailRef.current = editDetail;
  }, [editDetail]);

  useEffect(() => {
    if (!editDetail || isActualTimesOnlyMode(editDetail.status)) return;
    setGateForm(gateFormFromFlight(editDetail));
  }, [
    editDetail?.flightId,
    editDetail?.currentGateAssignment?.assignmentId,
    editDetail?.currentGateAssignment?.assignedFrom,
    editDetail?.currentGateAssignment?.assignedTo,
    editDetail?.currentGateAssignment?.gate?.gateId,
    editDetail?.scheduledDeparture,
    editDetail?.scheduledArrival,
  ]);

  const handleDetailRefresh = useCallback(
    (detail: FlightRs) => {
      setEditDetail(detail);
      syncEditFormFromDetail(detail, false);
    },
    [syncEditFormFromDetail],
  );

  const startEdit = useCallback(
    async (flight: FlightRs) => {
      setModalError(null);
      setEditingId(flight.flightId);
      try {
        const detail = await getFlightById(flight.flightId);
        setEditDetail(detail);
        syncEditFormFromDetail(detail);
        if (!isActualTimesOnlyMode(detail.status)) {
          const [allGates, allTypes] = await Promise.all([getGates(), getAircraftTypes()]);
          setGates(allGates);
          setAircraftTypes(allTypes);
        }
      } catch (e: unknown) {
        setEditingId(null);
        setEditDetail(null);
        setPageError(formatApiError(e));
      }
    },
    [setAircraftTypes, setGates, setPageError, syncEditFormFromDetail],
  );

  const cancelEdit = useCallback(() => {
    setModalError(null);
    setEditingId(null);
    setEditDetail(null);
    setStatusNew('');
    setStatusActualDeparture('');
    setStatusActualArrival('');
    setAcType('');
    setGateForm({ gateId: '', assignedFrom: '', assignedTo: '' });
    setDelayForm({ delayMinutes: '', reason: '' });
  }, []);

  const applyEditChanges = useCallback(async () => {
    if (!editDetail) return;

    const flightId = editDetail.flightId;

    if (isActualTimesOnlyMode(editDetail.status)) {
      const depChanged = statusActualDeparture !== toDatetimeLocalValue(editDetail.actualDeparture);
      const arrChanged = statusActualArrival !== toDatetimeLocalValue(editDetail.actualArrival);
      if (!depChanged && !arrChanged) {
        setModalError('Нет изменений для сохранения. Измените фактические времена и нажмите «Применить».');
        return;
      }
      setEditSaving(true);
      setModalError(null);
      try {
        const body: { actualDeparture?: string; actualArrival?: string } = {};
        if (depChanged) {
          const actualDeparture = fromDatetimeLocalValue(statusActualDeparture);
          if (!actualDeparture) {
            setModalError('Укажите корректное фактическое время вылета.');
            setEditSaving(false);
            return;
          }
          body.actualDeparture = actualDeparture;
        }
        if (arrChanged) {
          const actualArrival = fromDatetimeLocalValue(statusActualArrival);
          if (!actualArrival) {
            setModalError('Укажите корректное фактическое время прилёта.');
            setEditSaving(false);
            return;
          }
          body.actualArrival = actualArrival;
        }
        await correctActualTimes(flightId, body);
        cancelEdit();
        onSaved();
      } catch (e: unknown) {
        setModalError(formatApiError(e));
      } finally {
        setEditSaving(false);
      }
      return;
    }

    const currentAircraftId = editDetail.aircraftType?.aircraftTypeId;
    const resourceLocks = isClosedFlightStatus(editDetail.status);
    const gateMutable = isGateMutable(editDetail.status, editDetail.schedule);

    const aircraftChanged = !resourceLocks && acType && Number(acType) !== currentAircraftId;
    const gatePending = gateMutable && gateAssignmentChanged(gateForm, editDetail.currentGateAssignment);
    const statusPending = Boolean(statusNew);
    const delayFieldsActive =
      statusNew === 'DELAYED' || (editDetail.status === 'DELAYED' && !statusNew);
    const delayPending = delayFieldsActive && Boolean(delayForm.delayMinutes);
    const isActualTimeEditable = editDetail.status === 'SCHEDULED' || editDetail.status === 'DELAYED';
    const actualTimesChanged = isActualTimeEditable && (
      statusActualDeparture !== toDatetimeLocalValue(editDetail.actualDeparture) ||
      statusActualArrival !== toDatetimeLocalValue(editDetail.actualArrival)
    );

    if (!aircraftChanged && !gatePending && !statusPending && !delayPending && !actualTimesChanged) {
      setModalError('Нет изменений для сохранения. Измените поля в форме и нажмите «Применить».');
      return;
    }

    if (statusPending) {
      if (statusNew === 'DEPARTED' && !statusActualDeparture) {
        setModalError('Укажите фактическое время вылета для перевода в DEPARTED.');
        return;
      }
      if (statusNew === 'ARRIVED' && !statusActualArrival) {
        setModalError('Укажите фактическое время прилёта для перевода в ARRIVED.');
        return;
      }
      if (statusNew === 'DELAYED' && !delayForm.delayMinutes) {
        setModalError('При переводе в DELAYED укажите минуты задержки и причину.');
        return;
      }
    }

    setEditSaving(true);
    setModalError(null);
    try {
      if (aircraftChanged) {
        await assignAircraft(flightId, Number(acType));
      }
      if (gatePending) {
        const from = fromDatetimeLocalValue(gateForm.assignedFrom);
        const to = fromDatetimeLocalValue(gateForm.assignedTo);
        if (!from || !to) {
          setModalError('Укажите корректный интервал занятости гейта (с / по), формат даты YYYY-MM-DDTHH:mm.');
          setEditSaving(false);
          return;
        }
        await assignGate(flightId, {
          gateId: Number(gateForm.gateId),
          assignedFrom: from,
          assignedTo: to,
        });
      }
      if (statusPending) {
        const body: { status: string; actualDeparture?: string; actualArrival?: string } = {
          status: statusNew,
        };
        if (statusNew === 'DEPARTED') {
          const actualDeparture = fromDatetimeLocalValue(statusActualDeparture);
          if (!actualDeparture) {
            setModalError('Укажите корректное фактическое время вылета для перевода в DEPARTED.');
            setEditSaving(false);
            return;
          }
          body.actualDeparture = actualDeparture;
        }
        if (statusNew === 'ARRIVED') {
          const actualArrival = fromDatetimeLocalValue(statusActualArrival);
          if (!actualArrival) {
            setModalError('Укажите корректное фактическое время прилёта для перевода в ARRIVED.');
            setEditSaving(false);
            return;
          }
          body.actualArrival = actualArrival;
        }
        await updateFlightStatus(flightId, body);
      }
      if (delayPending) {
        await createDelayWarning(flightId, {
          delayMinutes: Number(delayForm.delayMinutes),
          reason: delayForm.reason || undefined,
        });
      }

      if (actualTimesChanged && !statusPending) {
        const body: { status: string; actualDeparture?: string; actualArrival?: string } = {
          status: editDetail.status,
        };
        if (statusActualDeparture !== toDatetimeLocalValue(editDetail.actualDeparture)) {
          const actualDeparture = fromDatetimeLocalValue(statusActualDeparture);
          if (actualDeparture) {
            body.actualDeparture = actualDeparture;
          }
        }
        if (statusActualArrival !== toDatetimeLocalValue(editDetail.actualArrival)) {
          const actualArrival = fromDatetimeLocalValue(statusActualArrival);
          if (actualArrival) {
            body.actualArrival = actualArrival;
          }
        }
        await updateFlightStatus(flightId, body);
      }

      cancelEdit();
      onSaved();
    } catch (e: unknown) {
      setModalError(formatApiError(e));
      if (editDetail) syncEditFormFromDetail(editDetail, false);
    } finally {
      setEditSaving(false);
    }
  }, [
    acType,
    cancelEdit,
    delayForm.delayMinutes,
    delayForm.reason,
    editDetail,
    gateForm,
    onSaved,
    statusActualArrival,
    statusActualDeparture,
    statusNew,
  ]);

  return {
    editingId,
    editDetail,
    modalError,
    statusNew,
    setStatusNew,
    statusActualDeparture,
    setStatusActualDeparture,
    statusActualArrival,
    setStatusActualArrival,
    acType,
    setAcType,
    gateForm,
    setGateForm,
    delayForm,
    setDelayForm,
    editSaving,
    editingDetailRef,
    startEdit,
    cancelEdit,
    applyEditChanges,
    handleDetailRefresh,
  };
}
