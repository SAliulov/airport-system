package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.AirlineRs;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.GateRq;
import ru.airport.dto.GateRs;
import ru.airport.dto.GateSummaryRs;
import ru.airport.dto.GateTimelineSegmentRs;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.security.AirportUserPrincipal;

import java.util.List;

@Component
public class DtoMapper {

    public UserProfileRs toUserProfileRs(AirportUserPrincipal principal) {
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
        return UserProfileRs.builder()
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .role(role)
                .build();
    }

    public AirlineRs toAirlineRs(Airline a) {
        if (a == null) {
            return null;
        }
        return AirlineRs.builder()
                .airlineId(a.getAirlineId())
                .iataCode(trimIata(a.getIataCode()))
                .name(a.getName())
                .country(a.getCountry())
                .build();
    }

    public Airline newAirline(AirlineRq rq) {
        return Airline.builder()
                .iataCode(rq.getIataCode())
                .name(rq.getName())
                .country(rq.getCountry())
                .build();
    }

    public void apply(AirlineRq rq, Airline a) {
        a.setIataCode(rq.getIataCode());
        a.setName(rq.getName());
        a.setCountry(rq.getCountry());
    }

    public AircraftTypeRs toAircraftTypeRs(AircraftType t) {
        if (t == null) {
            return null;
        }
        return AircraftTypeRs.builder()
                .aircraftTypeId(t.getAircraftTypeId())
                .icaoCode(t.getIcaoCode())
                .passengerCapacity(t.getPassengerCapacity())
                .sizeCategory(t.getSizeCategory())
                .build();
    }

    public AircraftType newAircraftType(AircraftTypeRq rq) {
        return AircraftType.builder()
                .icaoCode(rq.getIcaoCode())
                .passengerCapacity(rq.getPassengerCapacity())
                .sizeCategory(rq.getSizeCategory())
                .build();
    }

    public void apply(AircraftTypeRq rq, AircraftType t) {
        t.setIcaoCode(rq.getIcaoCode());
        t.setPassengerCapacity(rq.getPassengerCapacity());
        t.setSizeCategory(rq.getSizeCategory());
    }

    public GateRs toGateRs(Gate g) {
        if (g == null) {
            return null;
        }
        return GateRs.builder()
                .gateId(g.getGateId())
                .gateNumber(g.getGateNumber())
                .terminal(g.getTerminal())
                .isActive(g.getIsActive())
                .maxSizeCategory(g.getMaxSizeCategory())
                .build();
    }

    public GateSummaryRs toGateSummaryRs(Gate g) {
        if (g == null) {
            return null;
        }
        return GateSummaryRs.builder()
                .gateId(g.getGateId())
                .gateNumber(g.getGateNumber())
                .terminal(g.getTerminal())
                .build();
    }

    public Gate newGate(GateRq rq) {
        return Gate.builder()
                .gateNumber(rq.getGateNumber())
                .terminal(rq.getTerminal())
                .isActive(rq.getIsActive())
                .maxSizeCategory(rq.getMaxSizeCategory())
                .build();
    }

    public void apply(GateRq rq, Gate g) {
        g.setGateNumber(rq.getGateNumber());
        g.setTerminal(rq.getTerminal());
        g.setIsActive(rq.getIsActive());
        g.setMaxSizeCategory(rq.getMaxSizeCategory());
    }

    public ScheduleRs toScheduleRs(Schedule s) {
        if (s == null) {
            return null;
        }
        return ScheduleRs.builder()
                .scheduleId(s.getScheduleId())
                .flightNumber(s.getFlightNumber())
                .originAirport(trimAirport(s.getOriginAirport()))
                .destinationAirport(trimAirport(s.getDestinationAirport()))
                .scheduledDeparture(s.getScheduledDeparture())
                .scheduledArrival(s.getScheduledArrival())
                .airline(toAirlineRs(s.getAirline()))
                .build();
    }

    public Schedule newSchedule(ScheduleRq rq, Airline airline) {
        return Schedule.builder()
                .flightNumber(rq.getFlightNumber())
                .originAirport(rq.getOriginAirport())
                .destinationAirport(rq.getDestinationAirport())
                .scheduledDeparture(rq.getScheduledDeparture())
                .scheduledArrival(rq.getScheduledArrival())
                .airline(airline)
                .build();
    }

    public void apply(ScheduleRq rq, Schedule s, Airline airline) {
        s.setFlightNumber(rq.getFlightNumber());
        s.setOriginAirport(rq.getOriginAirport());
        s.setDestinationAirport(rq.getDestinationAirport());
        s.setScheduledDeparture(rq.getScheduledDeparture());
        s.setScheduledArrival(rq.getScheduledArrival());
        s.setAirline(airline);
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
                .actualDeparture(f.getActualDeparture())
                .actualArrival(f.getActualArrival())
                .schedule(toScheduleRs(f.getSchedule()))
                .aircraftType(toAircraftTypeRs(f.getAircraftType()))
                .currentGateAssignment(current)
                .gateAssignments(assignments)
                .delayWarnings(warnings)
                .build();
    }

    public GateAssignmentRs toGateAssignmentRs(GateAssignment ga) {
        return GateAssignmentRs.builder()
                .assignmentId(ga.getAssignmentId())
                .assignedFrom(ga.getAssignedFrom())
                .assignedTo(ga.getAssignedTo())
                .gate(toGateSummaryRs(ga.getGate()))
                .build();
    }

    public DelayWarningRs toDelayWarningRs(DelayWarning d) {
        return DelayWarningRs.builder()
                .warningId(d.getWarningId())
                .delayMinutes(d.getDelayMinutes())
                .reason(d.getReason())
                .createdAt(d.getCreatedAt())
                .build();
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

    public DelayWarning newDelayWarning(DelayWarningRq rq, Flight flight, java.time.LocalDateTime createdAt) {
        return DelayWarning.builder()
                .delayMinutes(rq.getDelayMinutes())
                .reason(rq.getReason())
                .createdAt(createdAt)
                .flight(flight)
                .build();
    }

    private static String trimIata(String code) {
        return code == null ? null : code.trim();
    }

    private static String trimAirport(String code) {
        return code == null ? null : code.trim();
    }
}
