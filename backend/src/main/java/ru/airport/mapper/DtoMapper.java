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
import ru.airport.dto.ScheduleSlotRq;
import ru.airport.dto.ScheduleSlotRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.security.AirportUserPrincipal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Фасад маппинга Entity ↔ DTO. Делегирует в специализированные mapper-классы.
 */
@Component
public class DtoMapper {

    private final UserDtoMapper userDtoMapper;
    private final DirectoryDtoMapper directoryDtoMapper;
    private final GateDtoMapper gateDtoMapper;
    private final ScheduleDtoMapper scheduleDtoMapper;
    private final FlightDtoMapper flightDtoMapper;

    public DtoMapper(
            UserDtoMapper userDtoMapper,
            DirectoryDtoMapper directoryDtoMapper,
            GateDtoMapper gateDtoMapper,
            ScheduleDtoMapper scheduleDtoMapper,
            FlightDtoMapper flightDtoMapper) {
        this.userDtoMapper = userDtoMapper;
        this.directoryDtoMapper = directoryDtoMapper;
        this.gateDtoMapper = gateDtoMapper;
        this.scheduleDtoMapper = scheduleDtoMapper;
        this.flightDtoMapper = flightDtoMapper;
    }

    public UserProfileRs toUserProfileRs(AirportUserPrincipal principal) {
        return userDtoMapper.toUserProfileRs(principal);
    }

    public AirlineRs toAirlineRs(Airline a) {
        return directoryDtoMapper.toAirlineRs(a);
    }

    public Airline newAirline(AirlineRq rq) {
        return directoryDtoMapper.newAirline(rq);
    }

    public void apply(AirlineRq rq, Airline a) {
        directoryDtoMapper.apply(rq, a);
    }

    public AircraftTypeRs toAircraftTypeRs(AircraftType t) {
        return directoryDtoMapper.toAircraftTypeRs(t);
    }

    public AircraftType newAircraftType(AircraftTypeRq rq) {
        return directoryDtoMapper.newAircraftType(rq);
    }

    public void apply(AircraftTypeRq rq, AircraftType t) {
        directoryDtoMapper.apply(rq, t);
    }

    public GateRs toGateRs(Gate g) {
        return gateDtoMapper.toGateRs(g);
    }

    public GateSummaryRs toGateSummaryRs(Gate g) {
        return gateDtoMapper.toGateSummaryRs(g);
    }

    public Gate newGate(GateRq rq) {
        return gateDtoMapper.newGate(rq);
    }

    public void apply(GateRq rq, Gate g) {
        gateDtoMapper.apply(rq, g);
    }

    public ScheduleRs toScheduleRs(Schedule s) {
        return scheduleDtoMapper.toScheduleRs(s);
    }

    public ScheduleRs toScheduleRs(Schedule s, LocalDate atDate) {
        return scheduleDtoMapper.toScheduleRs(s, atDate);
    }

    public ScheduleSlotRs toScheduleSlotRs(ru.airport.model.ScheduleSlot slot) {
        return scheduleDtoMapper.toScheduleSlotRs(slot);
    }

    public Schedule newSchedule(ScheduleRq rq, Airline airline) {
        return scheduleDtoMapper.newSchedule(rq, airline);
    }

    public void apply(ScheduleRq rq, Schedule s, Airline airline) {
        scheduleDtoMapper.apply(rq, s, airline);
    }

    public void applyFields(ScheduleRq rq, Schedule s, Airline airline) {
        scheduleDtoMapper.applyFields(rq, s, airline);
    }

    public void mergeSlots(List<ScheduleSlotRq> slotRqs, Schedule schedule, IntPredicate slotHasFlights) {
        scheduleDtoMapper.mergeSlots(slotRqs, schedule, slotHasFlights);
    }

    public FlightRs toFlightRsSummary(Flight f) {
        return flightDtoMapper.toFlightRsSummary(f);
    }

    public FlightRs toFlightRsDetail(Flight f) {
        return flightDtoMapper.toFlightRsDetail(f);
    }

    public GateAssignmentRs toGateAssignmentRs(GateAssignment ga) {
        return flightDtoMapper.toGateAssignmentRs(ga);
    }

    public DelayWarningRs toDelayWarningRs(DelayWarning d) {
        return flightDtoMapper.toDelayWarningRs(d);
    }

    public GateTimelineSegmentRs toTimelineSegment(GateAssignment ga) {
        return flightDtoMapper.toTimelineSegment(ga);
    }

    public DelayWarning newDelayWarning(DelayWarningRq rq, Flight flight, LocalDateTime createdAt) {
        return flightDtoMapper.newDelayWarning(rq, flight, createdAt);
    }
}
