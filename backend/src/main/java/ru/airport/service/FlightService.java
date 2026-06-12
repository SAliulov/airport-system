package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightActualTimesRq;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.FlightBulkDeleteRs;
import ru.airport.dto.FlightGenerateRq;
import ru.airport.dto.FlightGenerateRs;
import ru.airport.dto.FlightRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.GateRs;
import ru.airport.dto.PageRs;
import ru.airport.service.flight.FlightCommandService;
import ru.airport.service.flight.FlightQueryService;
import ru.airport.service.flight.FlightResourceService;
import ru.airport.service.flight.FlightStatusService;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад операций с рейсами для {@link ru.airport.controller.FlightController}.
 * Делегирует в специализированные сервисы пакета {@code service.flight}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightService {

    private final FlightQueryService flightQueryService;
    private final FlightCommandService flightCommandService;
    private final FlightStatusService flightStatusService;
    private final FlightResourceService flightResourceService;

    public PageRs<FlightRs> listAll(Integer page, Integer size, String sortDir) {
        return flightQueryService.listAll(page, size, sortDir);
    }

    public PageRs<FlightRs> filter(
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination,
            String terminal,
            Integer hourFrom,
            Integer page,
            Integer size,
            String sortDir
    ) {
        return flightQueryService.filter(date, statusRaw, airlineId, direction, origin, destination,
                terminal, hourFrom, page, size, sortDir);
    }

    public PageRs<FlightRs> search(
            String query,
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination,
            String terminal,
            Integer hourFrom,
            Integer page,
            Integer size,
            String sortDir
    ) {
        return flightQueryService.search(query, date, statusRaw, airlineId, direction, origin, destination,
                terminal, hourFrom, page, size, sortDir);
    }

    public FlightRs getById(Integer id) {
        return flightQueryService.getById(id);
    }

    public List<GateRs> listAvailableGates(Integer flightId) {
        return flightQueryService.listAvailableGates(flightId);
    }

    public List<AircraftTypeRs> listCompatibleAircraftTypes(Integer flightId) {
        return flightQueryService.listCompatibleAircraftTypes(flightId);
    }

    @Transactional
    public FlightRs create(FlightRq rq) {
        return flightCommandService.create(rq);
    }

    @Transactional
    public FlightGenerateRs generate(FlightGenerateRq rq) {
        return flightCommandService.generate(rq);
    }

    @Transactional
    public FlightRs update(Integer flightId, FlightRq rq) {
        return flightCommandService.update(flightId, rq);
    }

    @Transactional
    public FlightRs updateStatus(Integer flightId, FlightStatusUpdateRq rq) {
        return flightStatusService.updateStatus(flightId, rq);
    }

    @Transactional
    public FlightRs correctActualTimes(Integer flightId, FlightActualTimesRq rq) {
        return flightStatusService.correctActualTimes(flightId, rq);
    }

    @Transactional
    public FlightRs assignAircraft(Integer flightId, FlightAircraftAssignmentRq rq) {
        return flightResourceService.assignAircraft(flightId, rq);
    }

    @Transactional
    public GateAssignmentRs assignGate(Integer flightId, GateAssignmentRq rq) {
        return flightResourceService.assignGate(flightId, rq);
    }

    public List<DelayWarningRs> listDelayWarnings(Integer flightId) {
        return flightResourceService.listDelayWarnings(flightId);
    }

    @Transactional
    public DelayWarningRs addDelayWarning(Integer flightId, DelayWarningRq rq) {
        return flightResourceService.addDelayWarning(flightId, rq);
    }

    @Transactional
    public void delete(Integer id) {
        flightCommandService.delete(id);
    }

    @Transactional
    public FlightBulkDeleteRs deleteBySchedule(Integer scheduleId) {
        return flightCommandService.deleteBySchedule(scheduleId);
    }
}
