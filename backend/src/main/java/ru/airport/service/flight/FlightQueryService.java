package ru.airport.service.flight;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.config.AirportClock;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateRs;
import ru.airport.dto.PageRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.AircraftType;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.SizeCategory;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.FlightSpecifications;
import ru.airport.repository.GateRepository;
import ru.airport.validation.FlightStatusParser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static ru.airport.service.flight.FlightQuerySupport.matchesAirportDirection;
import static ru.airport.service.flight.FlightQuerySupport.normalizeAirport;
import static ru.airport.service.flight.FlightQuerySupport.normalizeSearchQuery;
import static ru.airport.service.flight.FlightQuerySupport.resolvePageIndex;
import static ru.airport.service.flight.FlightQuerySupport.resolvePageSize;
import static ru.airport.service.flight.FlightQuerySupport.touchCollections;
import static ru.airport.service.flight.FlightQuerySupport.trimUpper;

/**
 * Чтение рейсов: списки, фильтры, поиск, справочники для формы редактирования.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FlightQueryService {

    private final FlightRepository flightRepository;
    private final GateRepository gateRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final DtoMapper mapper;
    private final AirportClock airportClock;

    public PageRs<FlightRs> listAll(Integer page, Integer size, String sortDir) {
        return findFlightsPage(null, null, null, null, null, null, null, null, null, null, page, size, sortDir);
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
        return findFlightsPage(date, statusRaw, airlineId, direction, origin, destination, null,
                terminal, hourFrom, null, page, size, sortDir);
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
        return findFlightsPage(date, statusRaw, airlineId, direction, origin, destination, query,
                terminal, hourFrom, null, page, size, sortDir);
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

    Flight loadFlight(Integer id) {
        return flightRepository.findByIdForDetail(id)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", id));
    }

    private PageRs<FlightRs> findFlightsPage(
            LocalDate date,
            String statusRaw,
            Integer airlineId,
            String direction,
            String origin,
            String destination,
            String flightNumberQuery,
            String terminal,
            Integer hourFrom,
            Integer hourToExclusive,
            Integer page,
            Integer size,
            String sortDir
    ) {
        FlightStatus status = FlightStatusParser.parseOptional(statusRaw);
        LocalDateTime dayStart = airportClock.startOfDay(date);
        LocalDateTime dayEnd = airportClock.startOfNextDay(date);
        String dir = normalizeAirport(direction);
        String originIata = normalizeAirport(origin);
        String destinationIata = normalizeAirport(destination);
        String normalizedQuery = normalizeSearchQuery(flightNumberQuery);
        String normalizedTerminal = terminal != null && !terminal.isBlank() ? terminal.trim() : null;

        LocalDateTime hourWindowStart = null;
        LocalDateTime hourWindowEnd = null;
        if (hourFrom != null && dayStart != null) {
            hourWindowStart = dayStart.plusHours(hourFrom);
            hourWindowEnd = dayStart.plusHours(hourFrom + 2);
        } else if (hourFrom != null && hourToExclusive != null && dayStart != null) {
            hourWindowStart = dayStart.plusHours(hourFrom);
            hourWindowEnd = dayStart.plusHours(hourToExclusive);
        }

        int pageIndex = resolvePageIndex(page);
        int pageSize = resolvePageSize(size);
        Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? Sort.by(Sort.Order.desc("scheduledDeparture"), Sort.Order.desc("flightId"))
                : Sort.by(Sort.Order.asc("scheduledDeparture"), Sort.Order.asc("flightId"));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        var spec = FlightSpecifications.forApiList(
                dayStart, dayEnd, status, airlineId, normalizedQuery,
                normalizedTerminal, hourWindowStart, hourWindowEnd);

        Page<Flight> flightPage = flightRepository.findAll(spec, pageable);
        Stream<Flight> stream = flightPage.getContent().stream();
        if (originIata != null) {
            stream = stream.filter(f -> originIata.equals(trimUpper(f.getSchedule().getOriginAirport())));
        }
        if (destinationIata != null) {
            stream = stream.filter(f -> destinationIata.equals(trimUpper(f.getSchedule().getDestinationAirport())));
        }
        if (dir != null && originIata == null && destinationIata == null) {
            stream = stream.filter(f -> matchesAirportDirection(f, dir));
        }
        List<FlightRs> content = stream.map(mapper::toFlightRsSummary).toList();
        return PageRs.of(content, flightPage.getNumber(), flightPage.getSize(), flightPage.getTotalElements());
    }
}
