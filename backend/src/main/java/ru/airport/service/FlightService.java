package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.config.AirportClock;
import ru.airport.business.DelayWarningBusinessRules;
import ru.airport.business.FlightActualTimeRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightMutationBusinessRules;
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
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.GateRs;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.model.SizeCategory;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.FlightSpecifications;
import ru.airport.repository.GateAssignmentRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.validation.FlightStatusParser;
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
    private final FlightMutationBusinessRules flightMutationBusinessRules;
    private final FlightStatusBusinessRules flightStatusBusinessRules;
    private final FlightActualTimeRules flightActualTimeRules;
    private final DelayWarningBusinessRules delayWarningBusinessRules;
    private final RealtimeNotificationService realtimeNotificationService;
    private final AirportClock airportClock;

    @Value("${airport.home-iata}")
    private String homeIata;

    public List<FlightRs> listAll() {
        return flightRepository.findAllForApiList().stream()
                .map(mapper::toFlightRsSummary)
                .toList();
    }

    public List<FlightRs> filter(
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination
    ) {
        return findFlights(date, statusRaw, airlineId, direction, origin, destination, null);
    }

    public List<FlightRs> search(
            String query,
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination
    ) {
        return findFlights(date, statusRaw, airlineId, direction, origin, destination, query);
    }

    private List<FlightRs> findFlights(
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination,
            String flightNumberQuery
    ) {
        FlightStatus status = FlightStatusParser.parseOptional(statusRaw);
        LocalDateTime dayStart = date != null ? date.atStartOfDay() : null;
        LocalDateTime dayEnd = date != null ? date.plusDays(1).atStartOfDay() : null;
        String dir = normalizeAirport(direction);
        String originIata = normalizeAirport(origin);
        String destinationIata = normalizeAirport(destination);
        String normalizedQuery = normalizeSearchQuery(flightNumberQuery);

        List<Flight> flights;
        if (dayStart == null && dayEnd == null && status == null && airlineId == null
                && dir == null && originIata == null && destinationIata == null && normalizedQuery == null) {
            flights = flightRepository.findAllForApiList();
        } else {
            flights = flightRepository.findAll(
                    FlightSpecifications.forApiList(dayStart, dayEnd, status, airlineId, normalizedQuery));
        }

        Stream<Flight> stream = flights.stream();
        if (originIata != null) {
            stream = stream.filter(f -> originIata.equals(trimUpper(f.getSchedule().getOriginAirport())));
        }
        if (destinationIata != null) {
            stream = stream.filter(f -> destinationIata.equals(trimUpper(f.getSchedule().getDestinationAirport())));
        }
        if (dir != null && originIata == null && destinationIata == null) {
            stream = stream.filter(f -> matchesAirportDirection(f, dir));
        }
        return stream.map(mapper::toFlightRsSummary).toList();
    }

    private static String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
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

    public List<GateRs> listAvailableGates(Integer flightId) {
        Flight flight = loadFlight(flightId);
        AircraftType aircraft = flight.getAircraftType();
        List<Gate> gates;
        if (aircraft != null && aircraft.getSizeCategory() != null) {
            gates = gateRepository.findActiveGatesCompatibleWithAircraftSize(aircraft.getSizeCategory());
        } else {
            gates = gateRepository.findByIsActiveTrue();
        }
        return gates.stream().map(mapper::toGateRs).toList();
    }

    public List<AircraftTypeRs> listCompatibleAircraftTypes(Integer flightId) {
        Flight flight = loadFlight(flightId);
        touchCollections(flight);
        List<AircraftType> all = aircraftTypeRepository.findAll();
        GateAssignment active = flight.getActiveGateAssignment();
        if (active != null && active.getGate() != null && active.getGate().getMaxSizeCategory() != null) {
            SizeCategory gateMax = active.getGate().getMaxSizeCategory();
            return all.stream()
                    .filter(t -> t.getSizeCategory() != null
                            && SizeCategory.isCompatible(t.getSizeCategory(), gateMax))
                    .map(mapper::toAircraftTypeRs)
                    .toList();
        }
        return all.stream().map(mapper::toAircraftTypeRs).toList();
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
        FlightHomeAirportRules.assertScheduleTouchesHome(schedule, homeIata);
        Flight flight = Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build();
        return mapper.toFlightRsSummary(flightRepository.save(flight));
    }

    @Transactional
    public FlightRs update(Integer flightId, FlightRq rq) {
        Flight flight = loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        flightMutationBusinessRules.assertScheduleMutable(flight.getStatus());
        Schedule schedule = scheduleRepository.findById(rq.getScheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", rq.getScheduleId()));
        FlightHomeAirportRules.assertScheduleTouchesHome(schedule, homeIata);
        flight.setSchedule(schedule);
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }

    @Transactional
    public FlightRs updateStatus(Integer flightId, FlightStatusUpdateRq rq) {
        Flight flight = loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        touchCollections(flight);
        Schedule schedule = flight.getSchedule();
        FlightHomeAirportRules.OperationKind kind =
                FlightHomeAirportRules.resolveOperationKind(schedule, homeIata);
        flightStatusBusinessRules.assertManualTransition(flight.getStatus(), rq.getStatus(), kind);

        LocalDateTime now = airportClock.now();

        if (rq.getStatus() == FlightStatus.DEPARTED) {
            LocalDateTime actualDeparture = rq.getActualDeparture() != null
                    ? rq.getActualDeparture()
                    : (flight.getActualDeparture() != null ? flight.getActualDeparture() : now);
            flightActualTimeRules.assertActualDeparture(actualDeparture, now);
            if (kind == FlightHomeAirportRules.OperationKind.DEPARTURE) {
                FlightHomeAirportRules.assertManualTransitionToDeparted(flight, homeIata, actualDeparture);
            } else {
                FlightHomeAirportRules.assertManualInboundDeparture(flight, homeIata, actualDeparture);
            }
            flight.setActualDeparture(actualDeparture);
            GateAssignment active = flight.getActiveGateAssignment();
            if (active != null) {
                active.setAssignedTo(actualDeparture);
            }
        } else if (rq.getStatus() == FlightStatus.ARRIVED) {
            LocalDateTime actualArrival = rq.getActualArrival() != null
                    ? rq.getActualArrival()
                    : flight.getActualArrival();
            flightActualTimeRules.assertActualArrival(
                    actualArrival, flight.getActualDeparture(), now);
            if (kind == FlightHomeAirportRules.OperationKind.ARRIVAL) {
                FlightHomeAirportRules.assertManualTransitionToArrived(flight, homeIata, actualArrival);
            } else {
                FlightHomeAirportRules.assertManualRemoteArrival(flight, homeIata, actualArrival);
            }
            if (actualArrival != null) {
                flight.setActualArrival(actualArrival);
            }
        }

        flight.setStatus(rq.getStatus());
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }

    @Transactional
    public FlightRs assignAircraft(Integer flightId, FlightAircraftAssignmentRq rq) {
        Flight flight = loadFlight(flightId);
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
        Flight flight = loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        touchCollections(flight);
        flightMutationBusinessRules.assertResourcesMutable(flight.getStatus());
        FlightHomeAirportRules.assertScheduleTouchesHome(flight.getSchedule(), homeIata);
        Gate gate = gateRepository.findByIdForUpdate(rq.getGateId())
                .orElseThrow(() -> new ResourceNotFoundException("Gate", rq.getGateId()));

        gateAssignmentBusinessRules.assertGateIsActive(gate);
        gateAssignmentBusinessRules.assertValidInterval(rq.getAssignedFrom(), rq.getAssignedTo());

        List<GateAssignment> overlaps = gateAssignmentRepository.findOverlapping(
                gate.getGateId(),
                rq.getAssignedFrom(),
                rq.getAssignedTo(),
                null
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
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        delayWarningBusinessRules.assertMayAddManualDelayWarning(flight.getStatus());
        var entity = mapper.newDelayWarning(rq, flight, airportClock.now());
        DelayWarningRs rs = mapper.toDelayWarningRs(delayWarningRepository.save(entity));
        realtimeNotificationService.publishDelayWarning(flightId, rs);
        return rs;
    }

    @Transactional
    public void delete(Integer id) {
        Flight flight = loadFlight(id);
        flightMutationBusinessRules.assertFlightDeletable(flight.getStatus());
        touchCollections(flight);
        flightRepository.delete(flight);
    }

    private Flight loadFlight(Integer id) {
        return flightRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id));
    }
}
