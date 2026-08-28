package ru.airport.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.GateTimelineSegmentRs;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;
import java.util.List;

/** Entity ↔ DTO для рейсов, назначений гейтов и задержек. */
@Component
public class FlightDtoMapper {

    private static final Logger log = LoggerFactory.getLogger(FlightDtoMapper.class);

    private final ScheduleDtoMapper scheduleDtoMapper;
    private final GateDtoMapper gateDtoMapper;
    private final DirectoryDtoMapper directoryDtoMapper;

    public FlightDtoMapper(
            ScheduleDtoMapper scheduleDtoMapper,
            GateDtoMapper gateDtoMapper,
            DirectoryDtoMapper directoryDtoMapper) {
        this.scheduleDtoMapper = scheduleDtoMapper;
        this.gateDtoMapper = gateDtoMapper;
        this.directoryDtoMapper = directoryDtoMapper;
    }

    public FlightRs toFlightRsSummary(Flight f) {
        return toFlightRs(f, false, false);
    }

    public FlightRs toFlightRsDetail(Flight f) {
        return toFlightRs(f, true, true);
    }

    private FlightRs toFlightRs(Flight f, boolean withAssignments, boolean withWarnings) {
        GateAssignment active = f.getActiveGateAssignment();
        GateAssignmentRs current = active != null ? toGateAssignmentRs(active) : null;

        List<GateAssignmentRs> assignments = List.of();
        if (withAssignments && f.getGateAssignments() != null) {
            assignments = f.getGateAssignments().stream().map(this::toGateAssignmentRs).toList();
        }

        List<DelayWarningRs> warnings = List.of();
        if (withWarnings && f.getDelayWarnings() != null) {
            warnings = f.getDelayWarnings().stream().map(this::toDelayWarningRs).toList();
        }

        return FlightRs.builder()
                .flightId(f.getFlightId())
                .status(f.getStatus())
                .operationDate(f.getOperationDate())
                .scheduledDeparture(f.getScheduledDeparture())
                .scheduledArrival(f.getScheduledArrival())
                .actualDeparture(f.getActualDeparture())
                .actualArrival(f.getActualArrival())
                .schedule(scheduleDtoMapper.toScheduleRs(f.getSchedule()))
                .aircraftType(directoryDtoMapper.toAircraftTypeRs(f.getAircraftType()))
                .currentGateAssignment(current)
                .gateAssignments(assignments)
                .delayWarnings(warnings)
                .build();
    }

    public GateAssignmentRs toGateAssignmentRs(GateAssignment ga) {
        LocalDateTime from = ga.getAssignedFrom();
        LocalDateTime to = ga.getAssignedTo();
        if (from != null && to != null && !from.isBefore(to)) {
            log.warn(
                    "Gate assignment {} has inverted interval {} .. {} — normalizing for display",
                    ga.getAssignmentId(), from, to);
            LocalDateTime displayFrom = from.isBefore(to) ? from : to;
            LocalDateTime displayTo = from.isBefore(to) ? to : from;
            from = displayFrom;
            to = displayTo;
        }
        return GateAssignmentRs.builder()
                .assignmentId(ga.getAssignmentId())
                .assignedFrom(from)
                .assignedTo(to)
                .gate(gateDtoMapper.toGateSummaryRs(ga.getGate()))
                .build();
    }

    public DelayWarningRs toDelayWarningRs(DelayWarning d) {
        DelayWarningRs.DelayWarningRsBuilder builder = DelayWarningRs.builder()
                .warningId(d.getWarningId())
                .delayMinutes(d.getDelayMinutes())
                .reason(d.getReason())
                .createdAt(d.getCreatedAt());

        Flight flight = d.getFlight();
        if (flight != null) {
            builder.flightId(flight.getFlightId())
                    .scheduledDeparture(flight.getScheduledDeparture())
                    .flightStatus(flight.getStatus());
            Schedule schedule = flight.getSchedule();
            if (schedule != null) {
                builder.flightNumber(schedule.getFlightNumber())
                        .originAirport(schedule.getOriginAirport())
                        .destinationAirport(schedule.getDestinationAirport());
            }
        }
        return builder.build();
    }

    public GateTimelineSegmentRs toTimelineSegment(GateAssignment ga) {
        Flight fl = ga.getFlight();
        Schedule sch = fl.getSchedule();
        return GateTimelineSegmentRs.builder()
                .gateId(ga.getGate().getGateId())
                .gateNumber(ga.getGate().getGateNumber())
                .assignmentId(ga.getAssignmentId())
                .flightId(fl.getFlightId())
                .flightNumber(sch.getFlightNumber())
                .flightStatus(fl.getStatus())
                .assignedFrom(ga.getAssignedFrom())
                .assignedTo(ga.getAssignedTo())
                .build();
    }

    public DelayWarning newDelayWarning(DelayWarningRq rq, Flight flight, LocalDateTime createdAt) {
        return DelayWarning.builder()
                .delayMinutes(rq.getDelayMinutes())
                .reason(rq.getReason())
                .createdAt(createdAt)
                .flight(flight)
                .build();
    }
}
