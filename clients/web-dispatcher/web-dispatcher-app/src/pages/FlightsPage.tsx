import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ConnectionStatusBanner } from '../../../../shared/components/ConnectionStatusBanner';
import { ConfirmDialog } from '../shared/components/ConfirmDialog';
import PageStatus from '../components/PageStatus';
import { FlightCreateModal } from '../features/flights/components/FlightCreateModal';
import { FlightEditModal } from '../features/flights/components/FlightEditModal';
import { FlightGenerateModal } from '../features/flights/components/FlightGenerateModal';
import { FlightsFilterBar } from '../features/flights/components/FlightsFilterBar';
import { getFlightsRulesTooltip } from '../features/flights/constants';
import { isActualTimesOnlyMode } from '../features/flights/domain/flightRules';
import { useFlightEdit } from '../features/flights/hooks/useFlightEdit';
import { useFlightHighlight } from '../features/flights/hooks/useFlightHighlight';
import { useFlightPageModals } from '../features/flights/hooks/useFlightPageModals';
import { useFlightWebSocket } from '../features/flights/hooks/useFlightWebSocket';
import { useFlightsList } from '../features/flights/hooks/useFlightsList';
import { useDebouncedValue } from '../hooks/useDebouncedValue';
import { useRetryWhenBackendUp } from '../hooks/useRetryWhenBackendUp';
import {
  deleteFlight,
  getAirlines,
  getAircraftTypes,
  getGates,
  getSchedules,
} from '../services/api';
import { formatApiError } from '../utils/apiError';
import type { AircraftTypeRs, AirlineRs, GateRs, ScheduleRs, SizeCategory } from '../types';
import { todayAirportDate } from '../utils/airportTime';

/** Страница операционных рейсов: фильтры, CRUD, WS-sync. */
export default function FlightsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const initialDate = searchParams.get('date') || todayAirportDate();
  const initialSearch = searchParams.get('search')?.toUpperCase() ?? '';

  const [schedules, setSchedules] = useState<ScheduleRs[]>([]);
  const [airlines, setAirlines] = useState<AirlineRs[]>([]);
  const [aircraftTypes, setAircraftTypes] = useState<AircraftTypeRs[]>([]);
  const [gates, setGates] = useState<GateRs[]>([]);

  const [filterDate, setFilterDate] = useState(initialDate);
  const [filterStatus, setFilterStatus] = useState('');
  const [filterAirline, setFilterAirline] = useState('');
  const [filterOrigin, setFilterOrigin] = useState('');
  const [filterDestination, setFilterDestination] = useState('');
  const [searchQuery, setSearchQuery] = useState(initialSearch);
  const debouncedSearch = useDebouncedValue(searchQuery.trim(), 400);
  const [sortOrder, setSortOrder] = useState<'asc' | 'desc'>('asc');
  const [pendingDeleteId, setPendingDeleteId] = useState<number | null>(null);
  const [deleteSaving, setDeleteSaving] = useState(false);
const [notification, setNotification] = useState<string | null>(null);
const notificationTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const {
    flights,
    setFlights,
    page,
    setPage,
    totalPages,
    totalElements,
    pageSize,
    pageError,
    setPageError,
    load,
  } = useFlightsList({
    filterDate,
    filterStatus,
    filterAirline,
    filterOrigin,
    filterDestination,
    debouncedSearch,
    sortOrder,
  });

  const { highlightIds, highlight } = useFlightHighlight();

  const edit = useFlightEdit({
    onSaved: () => load(),
    setPageError,
    setGates,
    setAircraftTypes,
  });

  const { connected } = useFlightWebSocket({
    setFlights,
    highlight,
    editingDetailRef: edit.editingDetailRef,
    onDetailRefresh: edit.handleDetailRefresh,
    onOperationalEvent: (message) => {
      if (notificationTimerRef.current) clearTimeout(notificationTimerRef.current);
      setNotification(message);
      notificationTimerRef.current = setTimeout(() => setNotification(null), 5000);
    },
  });

  const modals = useFlightPageModals({
    schedules,
    setSchedules,
    filterDate,
    setFilterDate,
    load,
  });

  useEffect(() => {
    getAirlines().then(setAirlines).catch(e => setPageError(formatApiError(e)));
    getAircraftTypes().then(setAircraftTypes).catch(e => setPageError(formatApiError(e)));
    getGates().then(setGates).catch(e => setPageError(formatApiError(e)));
    getSchedules().then(setSchedules).catch(e => setPageError(formatApiError(e)));
  }, [setPageError]);

  const waitingForServer = useRetryWhenBackendUp(pageError, setPageError, () => load());

  async function confirmRemoveFlight() {
    if (pendingDeleteId == null) return;
    const id = pendingDeleteId;
    setDeleteSaving(true);
    try {
      await deleteFlight(id);
      if (edit.editingId === id) edit.cancelEdit();
      load();
      setPendingDeleteId(null);
    } catch (e: unknown) {
      setPageError(formatApiError(e));
    } finally {
      setDeleteSaving(false);
    }
  }

  function showCreatedFlightInList(showDate: string) {
    setFilterDate(showDate);
    modals.setCreateSuccess(null);
    setSearchParams(prev => {
      const next = new URLSearchParams(prev);
      next.set('date', showDate);
      return next;
    });
    load(showDate);
  }

  const actualTimesOnly = edit.editDetail ? isActualTimesOnlyMode(edit.editDetail.status) : false;

  const selectedAircraftSize: SizeCategory | undefined =
    (edit.acType ? aircraftTypes.find(t => String(t.aircraftTypeId) === edit.acType)?.sizeCategory : undefined)
    ?? edit.editDetail?.aircraftType?.sizeCategory;

  const selectedGateMax: SizeCategory | undefined =
    (edit.gateForm.gateId
      ? gates.find(g => String(g.gateId) === edit.gateForm.gateId)?.maxSizeCategory
      : undefined)
    ?? edit.editDetail?.currentGateAssignment?.gate?.maxSizeCategory;

  return (
    <div className="page flights-layout">
      <h1>
        Рейсы
        <span className="rules-info" title={getFlightsRulesTooltip()} aria-label="Правила блокировок по статусу">
          ⓘ
        </span>
      </h1>
      <PageStatus error={pageError} waitingForServer={waitingForServer} />
      <ConnectionStatusBanner connected={connected} />

      {notification && (
        <div className="page-notification" role="status">
          {notification}
          <button
            type="button"
            className="btn-ghost btn-sm"
            onClick={() => setNotification(null)}
            aria-label="Закрыть"
          >
            ✕
          </button>
        </div>
      )}

      {modals.createSuccess && (
        <div className="page-success-banner" role="status">
          Рейс #{modals.createSuccess.flightId} создан ({modals.createSuccess.label}).
          {modals.createSuccess.showDate !== filterDate ? (
            <>
              {' '}
              <button
                type="button"
                className="btn-link"
                onClick={() => showCreatedFlightInList(modals.createSuccess!.showDate)}
              >
                Показать в списке
              </button>
            </>
          ) : (
            <span> Отображается в таблице ниже.</span>
          )}
          <button
            type="button"
            className="btn-ghost btn-sm page-success-banner__close"
            onClick={() => modals.setCreateSuccess(null)}
            aria-label="Закрыть"
          >
            ✕
          </button>
        </div>
      )}

      <FlightCreateModal
        open={modals.createModalOpen}
        modalError={modals.modalError}
        schedules={schedules}
        availableSlots={modals.availableSlots}
        modalScheduleFilter={modals.modalScheduleFilter}
        onModalScheduleFilterChange={modals.setModalScheduleFilter}
        modalSlotId={modals.modalSlotId}
        onModalSlotIdChange={modals.setModalSlotId}
        modalOperationDate={modals.modalOperationDate}
        onModalOperationDateChange={modals.setModalOperationDate}
        selectedCreateSlot={modals.selectedCreateSlot}
        createDateHint={modals.createDateHint}
        createSaving={modals.createSaving}
        onClose={modals.closeCreateModal}
        onSubmit={() => void modals.submitCreateFlight()}
      />

      <FlightGenerateModal
        open={modals.generateModalOpen}
        generateFrom={modals.generateFrom}
        onGenerateFromChange={modals.setGenerateFrom}
        generateTo={modals.generateTo}
        onGenerateToChange={modals.setGenerateTo}
        generateScheduleId={modals.generateScheduleId}
        onGenerateScheduleIdChange={modals.setGenerateScheduleId}
        generateInfo={modals.generateInfo}
        generateSaving={modals.generateSaving}
        bulkDeleteSaving={modals.bulkDeleteSaving}
        schedules={schedules}
        onClose={() => modals.setGenerateModalOpen(false)}
        onSubmit={() => void modals.submitGenerate()}
        onBulkDeleteBySchedule={modals.requestBulkDeleteBySchedule}
      />

      <ConfirmDialog
        open={pendingDeleteId != null}
        title="Удалить рейс?"
        message="Действие необратимо."
        variant="danger"
        busy={deleteSaving}
        onCancel={() => setPendingDeleteId(null)}
        onConfirm={() => void confirmRemoveFlight()}
      />

      <ConfirmDialog
        open={modals.bulkDeleteConfirmLabel != null}
        title={`Удалить все рейсы шаблона ${modals.bulkDeleteConfirmLabel ?? ''}?`}
        message="Уже вылетевшие/прибывшие рейсы сервер не удалит."
        variant="danger"
        busy={modals.bulkDeleteSaving}
        onCancel={modals.cancelBulkDeleteConfirm}
        onConfirm={() => void modals.confirmBulkDeleteBySchedule()}
      />

      {edit.editingId != null && edit.editDetail && (
        <FlightEditModal
          editingId={edit.editingId}
          editDetail={edit.editDetail}
          modalError={edit.modalError}
          actualTimesOnly={actualTimesOnly}
          statusNew={edit.statusNew}
          onStatusNewChange={edit.setStatusNew}
          statusActualDeparture={edit.statusActualDeparture}
          onStatusActualDepartureChange={edit.setStatusActualDeparture}
          statusActualArrival={edit.statusActualArrival}
          onStatusActualArrivalChange={edit.setStatusActualArrival}
          acType={edit.acType}
          onAcTypeChange={edit.setAcType}
          gateForm={edit.gateForm}
          onGateFormChange={edit.setGateForm}
          delayForm={edit.delayForm}
          onDelayFormChange={edit.setDelayForm}
          aircraftTypes={aircraftTypes}
          gates={gates}
          selectedAircraftSize={selectedAircraftSize}
          selectedGateMax={selectedGateMax}
          editSaving={edit.editSaving}
          onClose={edit.cancelEdit}
          onApply={() => void edit.applyEditChanges()}
        />
      )}

      <FlightsFilterBar
        filterDate={filterDate}
        onFilterDateChange={setFilterDate}
        filterStatus={filterStatus}
        onFilterStatusChange={setFilterStatus}
        filterAirline={filterAirline}
        onFilterAirlineChange={setFilterAirline}
        filterOrigin={filterOrigin}
        onFilterOriginChange={setFilterOrigin}
        filterDestination={filterDestination}
        onFilterDestinationChange={setFilterDestination}
        searchQuery={searchQuery}
        onSearchQueryChange={setSearchQuery}
        sortOrder={sortOrder}
        onSortOrderToggle={() => setSortOrder(prev => (prev === 'asc' ? 'desc' : 'asc'))}
        airlines={airlines}
        onOpenCreate={() => void modals.openCreateModal()}
        onOpenGenerate={modals.openGenerateModal}
        flights={flights}
        editingId={edit.editingId}
        highlightIds={highlightIds}
        onStartEdit={flight => void edit.startEdit(flight)}
        onRemoveFlight={id => setPendingDeleteId(id)}
        page={page}
        totalPages={totalPages}
        totalElements={totalElements}
        pageSize={pageSize}
        onPageChange={setPage}
      />
    </div>
  );
}
