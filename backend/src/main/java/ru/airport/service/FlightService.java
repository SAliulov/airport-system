package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightStatusBusinessRules;
import ru.airport.business.GateAssignmentBusinessRules;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.FlightRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.AircraftType;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.FlightSpecifications;
import ru.airport.repository.GateAssignmentRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightService {

    private final FlightRepository flightRepository;
    private final ScheduleRepository scheduleRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final GateRepository gateRepository;
    private final GateAssignmentRepository gateAssignmentRepository;
    private final DelayWarningRepository delayWarningRepository;
    private final DtoMapper mapper;
    private final GateAssignmentBusinessRules gateAssignmentBusinessRules;
    private final FlightStatusBusinessRules flightStatusBusinessRules;
    private final RealtimeNotificationService realtimeNotificationService;

    /**
     * Список рейсов с опциональными фильтрами (табло FirstLab §2: дата, статус, авиакомпания, направление IATA).
     */
    public List<FlightRs> list(LocalDate date, FlightStatus status, Integer airlineId, String direction) {
        LocalDateTime dayStart = date != null ? date.atStartOfDay() : null;
        LocalDateTime dayEnd = date != null ? date.plusDays(1).atStartOfDay() : null;
        String dir = normalizeAirport(direction);

        List<Flight> flights;
        if (dayStart == null && dayEnd == null && status == null && airlineId == null && dir == null) {
            flights = flightRepository.findAllForApiList();
        } else {
            flights = flightRepository.findAll(FlightSpecifications.forApiList(dayStart, dayEnd, status, airlineId));
        }

        Stream<Flight> stream = flights.stream();
        if (dir != null) {
            stream = stream.filter(f -> matchesAirportDirection(f, dir));
        }
        return stream.map(mapper::toFlightRsSummary).toList();
    }

    private static String normalizeAirport(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return direction.trim().toUpperCase();
    }

    private static boolean matchesAirportDirection(Flight flight, String airportIataUpper) {
        Schedule s = flight.getSchedule();
        if (s == null) {
            return false;
        }
        String o = trimUpper(s.getOriginAirport());
        String d = trimUpper(s.getDestinationAirport());
        return airportIataUpper.equals(o) || airportIataUpper.equals(d);
    }

    private static String trimUpper(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    public FlightRs getById(Integer id) {
        Flight f = loadFlight(id);
        touchCollections(f);
        return mapper.toFlightRsDetail(f);
    }

    private static void touchCollections(Flight f) {
        if (f.getGateAssignments() != null) {
            f.getGateAssignments().size();
        }
        if (f.getDelayWarnings() != null) {
            f.getDelayWarnings().size();
        }
    }

    @Transactional
    public FlightRs create(FlightRq rq) {
        Schedule schedule = scheduleRepository.findById(rq.getScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", rq.getScheduleId()));
        Flight flight = Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build();
        return mapper.toFlightRsSummary(flightRepository.save(flight));
    }

    @Transactional
    public FlightRs updateStatus(Integer flightId, FlightStatusUpdateRq rq) {
        Flight flight = loadFlight(flightId);
        flightStatusBusinessRules.assertManualTransition(flight.getStatus(), rq.getStatus());
        flight.setStatus(rq.getStatus());
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }

    @Transactional
    public FlightRs assignAircraft(Integer flightId, FlightAircraftAssignmentRq rq) {
        Flight flight = loadFlight(flightId);
        AircraftType type = aircraftTypeRepository.findById(rq.getAircraftTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("AircraftType", rq.getAircraftTypeId()));
        flight.setAircraftType(type);
        GateAssignment active = flight.getActiveGateAssignment();
        if (active != null) {
            gateAssignmentBusinessRules.assertAircraftFitsGate(type, active.getGate());
        }
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }

    @Transactional
    public GateAssignmentRs assignGate(Integer flightId, GateAssignmentRq rq) {
        Flight flight = loadFlight(flightId);
        Gate gate = gateRepository.findById(rq.getGateId())
                .orElseThrow(() -> new ResourceNotFoundException("Gate", rq.getGateId()));

        gateAssignmentBusinessRules.assertGateIsActive(gate);
        gateAssignmentBusinessRules.assertValidInterval(rq.getAssignedFrom(), rq.getAssignedTo());

        List<GateAssignment> overlaps = gateAssignmentRepository.findOverlapping(
                gate.getGateId(),
                rq.getAssignedFrom(),
                rq.getAssignedTo(),
                flight.getFlightId()
        );
        gateAssignmentBusinessRules.assertNoOverlaps(overlaps);
        gateAssignmentBusinessRules.assertAircraftFitsGate(flight.getAircraftType(), gate);

        GateAssignment ga = GateAssignment.builder()
                .flight(flight)
                .gate(gate)
                .assignedFrom(rq.getAssignedFrom())
                .assignedTo(rq.getAssignedTo())
                .build();
        GateAssignment saved = gateAssignmentRepository.save(ga);
        flight.getGateAssignments().add(saved);
        GateAssignmentRs rs = mapper.toGateAssignmentRs(saved);
        realtimeNotificationService.publishGateChange(flight.getFlightId(), rs);
        return rs;
    }

    public List<DelayWarningRs> listDelayWarnings(Integer flightId) {
        loadFlight(flightId);
        return delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flightId).stream()
                .map(mapper::toDelayWarningRs)
                .toList();
    }

    @Transactional
    public DelayWarningRs addDelayWarning(Integer flightId, DelayWarningRq rq) {
        Flight flight = loadFlight(flightId);
        LocalDateTime now = LocalDateTime.now();
        var entity = mapper.newDelayWarning(rq, flight, now);
        DelayWarningRs rs = mapper.toDelayWarningRs(delayWarningRepository.save(entity));
        realtimeNotificationService.publishDelayWarning(flightId, rs);
        return rs;
    }

    private Flight loadFlight(Integer id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id));
    }
}
