package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.business.SchedulePeriodicityBusinessRules;
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
import ru.airport.dto.ScheduleSlotRq;
import ru.airport.dto.ScheduleSlotRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.exception.BadRequestException;
import ru.airport.exception.ConflictException;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;
import ru.airport.security.AirportUserPrincipal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

@Component
public class DtoMapper {

    private final SchedulePeriodicityBusinessRules periodicityRules;

    public DtoMapper(SchedulePeriodicityBusinessRules periodicityRules) {
        this.periodicityRules = periodicityRules;
    }

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
                .maxSizeCategory(g.getMaxSizeCategory())
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
        return toScheduleRs(s, null);
    }

    public ScheduleRs toScheduleRs(Schedule s, LocalDate atDate) {
        if (s == null) {
            return null;
        }
        List<ScheduleSlotRs> slotRs = s.getSlots() != null
                ? s.getSlots().stream().map(this::toScheduleSlotRs).toList()
                : List.of();
        ScheduleRs.ScheduleRsBuilder builder = ScheduleRs.builder()
                .scheduleId(s.getScheduleId())
                .flightNumber(s.getFlightNumber())
                .originAirport(trimAirport(s.getOriginAirport()))
                .destinationAirport(trimAirport(s.getDestinationAirport()))
                .effectiveFrom(s.getEffectiveFrom())
                .effectiveTo(s.getEffectiveTo())
                .isActive(s.getIsActive())
                .periodicityType(s.getPeriodicityType())
                .periodicityStep(s.getPeriodicityStep())
                .airline(toAirlineRs(s.getAirline()))
                .slots(slotRs);
        if (atDate != null && s.getSlots() != null) {
            for (ScheduleSlot slot : s.getSlots()) {
                if (periodicityRules.matchesOperationDate(s, slot, atDate)) {
                    builder.departureAtDate(LocalDateTime.of(atDate, slot.getDepartureTime()));
                    LocalDate arrivalDate = atDate;
                    if (!slot.getArrivalTime().isAfter(slot.getDepartureTime())) {
                        arrivalDate = atDate.plusDays(1);
                    }
                    builder.arrivalAtDate(LocalDateTime.of(arrivalDate, slot.getArrivalTime()));
                    break;
                }
            }
        }
        return builder.build();
    }

    public ScheduleSlotRs toScheduleSlotRs(ScheduleSlot slot) {
        if (slot == null) {
            return null;
        }
        return ScheduleSlotRs.builder()
                .slotId(slot.getSlotId())
                .dayOfWeek(slot.getDayOfWeek())
                .departureTime(slot.getDepartureTime())
                .arrivalTime(slot.getArrivalTime())
                .build();
    }

    public Schedule newSchedule(ScheduleRq rq, Airline airline) {
        Schedule schedule = Schedule.builder()
                .flightNumber(rq.getFlightNumber())
                .originAirport(rq.getOriginAirport())
                .destinationAirport(rq.getDestinationAirport())
                .effectiveFrom(rq.getEffectiveFrom())
                .effectiveTo(rq.getEffectiveTo())
                .isActive(rq.getIsActive() != null ? rq.getIsActive() : true)
                .periodicityType(rq.getPeriodicityType())
                .periodicityStep(rq.getPeriodicityStep())
                .airline(airline)
                .build();
        applySlots(rq.getSlots(), schedule);
        return schedule;
    }

    public void apply(ScheduleRq rq, Schedule s, Airline airline) {
        applyFields(rq, s, airline);
    }

    public void applyFields(ScheduleRq rq, Schedule s, Airline airline) {
        s.setFlightNumber(rq.getFlightNumber());
        s.setOriginAirport(rq.getOriginAirport());
        s.setDestinationAirport(rq.getDestinationAirport());
        s.setEffectiveFrom(rq.getEffectiveFrom());
        s.setEffectiveTo(rq.getEffectiveTo());
        if (rq.getIsActive() != null) {
            s.setIsActive(rq.getIsActive());
        }
        s.setPeriodicityType(rq.getPeriodicityType());
        s.setPeriodicityStep(rq.getPeriodicityStep());
        s.setAirline(airline);
    }

    /**
     * Обновляет слоты in-place по slotId; новые без id — insert; лишние — delete если нет рейсов.
     */
    public void mergeSlots(List<ScheduleSlotRq> slotRqs, Schedule schedule, IntPredicate slotHasFlights) {
        if (slotRqs == null) {
            return;
        }
        Map<Integer, ScheduleSlot> existingById = schedule.getSlots().stream()
                .filter(s -> s.getSlotId() != null)
                .collect(Collectors.toMap(ScheduleSlot::getSlotId, s -> s));
        Set<Integer> keptIds = new HashSet<>();

        for (ScheduleSlotRq rq : slotRqs) {
            if (rq.getSlotId() != null) {
                ScheduleSlot slot = existingById.get(rq.getSlotId());
                if (slot == null) {
                    throw new BadRequestException("Слот с id " + rq.getSlotId() + " не найден в расписании");
                }
                slot.setDayOfWeek(rq.getDayOfWeek());
                slot.setDepartureTime(rq.getDepartureTime());
                slot.setArrivalTime(rq.getArrivalTime());
                keptIds.add(rq.getSlotId());
            } else {
                schedule.getSlots().add(ScheduleSlot.builder()
                        .dayOfWeek(rq.getDayOfWeek())
                        .departureTime(rq.getDepartureTime())
                        .arrivalTime(rq.getArrivalTime())
                        .schedule(schedule)
                        .build());
            }
        }

        List<ScheduleSlot> toRemove = schedule.getSlots().stream()
                .filter(s -> s.getSlotId() != null && !keptIds.contains(s.getSlotId()))
                .toList();
        for (ScheduleSlot orphan : toRemove) {
            if (slotHasFlights.test(orphan.getSlotId())) {
                throw new ConflictException(
                        "Нельзя удалить слот: по нему уже созданы рейсы. Оставьте слот в форме или удалите рейсы.");
            }
            schedule.getSlots().remove(orphan);
        }
    }

    private void applySlots(List<ScheduleSlotRq> slotRqs, Schedule schedule) {
        if (slotRqs == null) {
            return;
        }
        List<ScheduleSlot> slots = new ArrayList<>();
        for (ScheduleSlotRq rq : slotRqs) {
            ScheduleSlot slot = ScheduleSlot.builder()
                    .dayOfWeek(rq.getDayOfWeek())
                    .departureTime(rq.getDepartureTime())
                    .arrivalTime(rq.getArrivalTime())
                    .schedule(schedule)
                    .build();
            slots.add(slot);
        }
        schedule.getSlots().addAll(slots);
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
