package ru.airport.service.flight;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.DelayWarningBusinessRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.business.GateAssignmentBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.AircraftType;
import ru.airport.model.Flight;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.GateAssignmentRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDateTime;
import java.util.List;

import static ru.airport.service.flight.FlightQuerySupport.touchCollections;

/**
 * Назначение гейта и типа ВС, предупреждения о задержке.
 */
@Service
@RequiredArgsConstructor
public class FlightResourceService {

    private final FlightRepository flightRepository;
    private final GateRepository gateRepository;
    private final GateAssignmentRepository gateAssignmentRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final DelayWarningRepository delayWarningRepository;
    private final DtoMapper mapper;
    private final GateAssignmentBusinessRules gateAssignmentBusinessRules;
    private final FlightMutationBusinessRules flightMutationBusinessRules;
    private final DelayWarningBusinessRules delayWarningBusinessRules;
    private final FlightHomeAirportRules flightHomeAirportRules;
    private final RealtimeNotificationService realtimeNotificationService;
    private final AirportClock airportClock;
    private final AirportProperties airportProperties;
    private final FlightQueryService flightQueryService;

    private String homeIata() {
        return airportProperties.getHomeIata();
    }

    @Transactional
    public FlightRs assignAircraft(Integer flightId, FlightAircraftAssignmentRq rq) {
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        flightMutationBusinessRules.assertResourcesMutable(flight.getStatus());
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
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        touchCollections(flight);
        FlightHomeAirportRules.OperationKind gateKind =
                flightHomeAirportRules.resolveOperationKind(flight.getSchedule(), homeIata());
        flightMutationBusinessRules.assertGateMutable(flight.getStatus(), gateKind);
        Gate gate = gateRepository.findByIdForUpdate(rq.getGateId())
                .orElseThrow(() -> new ResourceNotFoundException("Gate", rq.getGateId()));

        flightHomeAirportRules.assertValidHomeRoute(flight.getSchedule(), homeIata());
        gateAssignmentBusinessRules.assertGateIsActive(gate);
        gateAssignmentBusinessRules.assertValidInterval(rq.getAssignedFrom(), rq.getAssignedTo());
        gateAssignmentBusinessRules.assertAssignmentStartsNotInPast(rq.getAssignedFrom(), airportClock.now());

        FlightHomeAirportRules.OperationKind kind =
                flightHomeAirportRules.resolveOperationKind(flight.getSchedule(), homeIata());
        LocalDateTime scheduledAnchor = kind == FlightHomeAirportRules.OperationKind.DEPARTURE
                ? flight.getScheduledDeparture()
                : flight.getScheduledArrival();
        gateAssignmentBusinessRules.assertIntervalOverlapsScheduledWindow(
                rq.getAssignedFrom(), rq.getAssignedTo(), scheduledAnchor);

        // Закрываем предыдущие назначения рейса (чистое правило → мутация в сервисе)
        LocalDateTime closingTime = gateAssignmentBusinessRules.computeClosingTime(
                flight.getGateAssignments(), rq.getAssignedFrom());
        if (closingTime != null && flight.getGateAssignments() != null) {
            for (GateAssignment ga : flight.getGateAssignments()) {
                if (ga.getAssignedTo() == null || !ga.getAssignedTo().isAfter(closingTime)) {
                    continue;
                }
                ga.setAssignedTo(closingTime);
                if (!ga.getAssignedFrom().isBefore(ga.getAssignedTo())) {
                    ga.setAssignedTo(ga.getAssignedFrom().plusMinutes(1));
                }
            }
        }

        List<GateAssignment> overlaps = gateAssignmentRepository.findOverlappingForOtherFlights(
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

    @Transactional(readOnly = true)
    public List<DelayWarningRs> listDelayWarnings(Integer flightId) {
        flightQueryService.loadFlight(flightId);
        return delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flightId).stream()
                .map(mapper::toDelayWarningRs)
                .toList();
    }

    @Transactional
    public DelayWarningRs addDelayWarning(Integer flightId, DelayWarningRq rq) {
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        delayWarningBusinessRules.assertMayAddManualDelayWarning(flight.getStatus());
        var entity = mapper.newDelayWarning(rq, flight, airportClock.now());
        DelayWarningRs rs = mapper.toDelayWarningRs(delayWarningRepository.save(entity));
        realtimeNotificationService.publishDelayWarning(flightId, rs);
        return rs;
    }
}
