package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
import ru.airport.repository.GateAssignmentRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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

    public List<FlightRs> list(LocalDate date, FlightStatus status) {
        List<Flight> flights;
        if (date == null) {
            if (status == null) {
                flights = flightRepository.findAll(Sort.by(Sort.Order.asc("schedule.scheduledDeparture")));
            } else {
                flights = flightRepository.findByStatus(status).stream()
                        .sorted(Comparator.comparing(f -> f.getSchedule().getScheduledDeparture()))
                        .toList();
            }
        } else {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            flights = flightRepository.findByScheduleDayAndOptionalStatus(start, end, status);
        }
        return flights.stream().map(mapper::toFlightRsSummary).toList();
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
        return mapper.toFlightRsSummary(flightRepository.save(flight));
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
        return mapper.toFlightRsSummary(flightRepository.save(flight));
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
        return mapper.toGateAssignmentRs(saved);
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
        return mapper.toDelayWarningRs(delayWarningRepository.save(entity));
    }

    private Flight loadFlight(Integer id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id));
    }
}
