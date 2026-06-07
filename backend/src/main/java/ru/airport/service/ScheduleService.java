package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.ScheduleBusinessRules;
import ru.airport.business.SchedulePeriodicityBusinessRules;
import ru.airport.business.ScheduleSlotBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleRs;
import ru.airport.exception.ConflictException;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Schedule;
import ru.airport.repository.AirlineRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.FlightSpecifications;
import ru.airport.repository.ScheduleRepository;
import ru.airport.validation.FlightStatusParser;
import ru.airport.validation.TextNormalization;
import ru.airport.service.schedule.ScheduleDraftMapper;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final DtoMapper mapper;
    private final ScheduleBusinessRules scheduleBusinessRules;
    private final ScheduleSlotBusinessRules scheduleSlotBusinessRules;
    private final SchedulePeriodicityBusinessRules periodicityRules;
    private final FlightHomeAirportRules flightHomeAirportRules;
    private final AirportProperties airportProperties;
    private final AirportClock airportClock;
    private final RealtimeNotificationService realtimeNotificationService;

    private static final DateTimeFormatter DEP_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private String homeIata() {
        return airportProperties.getHomeIata();
    }

    public List<ScheduleRs> listAll() {
        return scheduleRepository.findAll(Sort.by(Sort.Order.asc("flightNumber"))).stream()
                .map(mapper::toScheduleRs)
                .toList();
    }

    public List<ScheduleRs> filter(
            LocalDate date,
            Integer airlineId,
            String statusRaw,
            String direction,
            String origin,
            String destination
    ) {
        return findSchedules(date, airlineId, statusRaw, null, direction, origin, destination);
    }

    public List<ScheduleRs> search(
            String query,
            LocalDate date,
            Integer airlineId,
            String statusRaw,
            String direction,
            String origin,
            String destination
    ) {
        return findSchedules(date, airlineId, statusRaw, query, direction, origin, destination);
    }

    private List<ScheduleRs> findSchedules(
            LocalDate date,
            Integer airlineId,
            String statusRaw,
            String search,
            String direction,
            String origin,
            String destination
    ) {
        FlightStatus status = FlightStatusParser.parseOptional(statusRaw);
        List<Schedule> candidates = resolveCandidates(date, status);
        String q = search != null ? search.trim() : "";
        String dir = normalizeAirport(direction);
        String originIata = normalizeAirport(origin);
        String destinationIata = normalizeAirport(destination);
        return candidates.stream()
                .filter(s -> airlineId == null || s.getAirline().getAirlineId().equals(airlineId))
                .filter(s -> q.isEmpty() || s.getFlightNumber().toLowerCase().contains(q.toLowerCase()))
                .filter(s -> originIata == null || originIata.equals(trimUpper(s.getOriginAirport())))
                .filter(s -> destinationIata == null || destinationIata.equals(trimUpper(s.getDestinationAirport())))
                .filter(s -> dir == null || originIata != null || destinationIata != null || matchesDirection(s, dir))
                .sorted(Comparator.comparing(Schedule::getFlightNumber))
                .map(s -> mapper.toScheduleRs(s, date))
                .toList();
    }

    private static String trimUpper(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static String normalizeAirport(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return direction.trim().toUpperCase();
    }

    private static boolean matchesDirection(Schedule s, String airportIataUpper) {
        String o = s.getOriginAirport() != null ? s.getOriginAirport().trim().toUpperCase() : "";
        String d = s.getDestinationAirport() != null ? s.getDestinationAirport().trim().toUpperCase() : "";
        return airportIataUpper.equals(o) || airportIataUpper.equals(d);
    }

    private List<Schedule> resolveCandidates(LocalDate date, FlightStatus status) {
        if (date != null) {
            LocalDateTime start = airportClock.startOfDay(date);
            LocalDateTime end = airportClock.startOfNextDay(date);
            if (status != null) {
                List<Flight> flights = flightRepository.findAll(
                        FlightSpecifications.forApiList(start, end, status, null, null, null, null, null));
                return distinctSchedules(flights.stream().map(Flight::getSchedule).toList());
            }
            return scheduleRepository.findAllActiveWithSlots().stream()
                    .filter(s -> s.getSlots() != null && s.getSlots().stream()
                            .anyMatch(slot -> periodicityRules.matchesOperationDate(s, slot, date)))
                    .toList();
        }
        if (status != null) {
            List<Flight> flights = flightRepository.findByStatus(status);
            return distinctSchedules(flights.stream().map(Flight::getSchedule).toList()).stream()
                    .sorted(Comparator.comparing(Schedule::getFlightNumber))
                    .toList();
        }
        return scheduleRepository.findAll(Sort.by(Sort.Order.asc("flightNumber")));
    }

    private static List<Schedule> distinctSchedules(List<Schedule> schedules) {
        Map<Integer, Schedule> map = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            map.putIfAbsent(s.getScheduleId(), s);
        }
        return new ArrayList<>(map.values());
    }

    public ScheduleRs getById(Integer id) {
        return enrichScheduleRs(mapper.toScheduleRs(loadSchedule(id)));
    }

    @Transactional
    public ScheduleRs create(ScheduleRq rq) {
        TextNormalization.normalizeScheduleAirports(rq);
        scheduleBusinessRules.assertValidEffectiveRange(rq.getEffectiveFrom(), rq.getEffectiveTo());
        scheduleBusinessRules.assertValidPeriodicity(rq.getPeriodicityType(), rq.getPeriodicityStep());
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        Schedule draft = mapper.newSchedule(rq, airline);
        scheduleSlotBusinessRules.assertValidSlots(draft, ScheduleDraftMapper.slotsFrom(rq.getSlots()));
        flightHomeAirportRules.assertValidHomeRoute(draft, homeIata());
        Schedule saved = scheduleRepository.save(draft);
        return enrichScheduleRs(mapper.toScheduleRs(saved));
    }

    @Transactional
    public ScheduleRs update(Integer id, ScheduleRq rq) {
        TextNormalization.normalizeScheduleAirports(rq);
        Schedule existing = loadSchedule(id);
        List<Flight> linkedFlights = flightRepository.findBySchedule_ScheduleId(id);
        scheduleBusinessRules.assertMayUpdate(existing, ScheduleDraftMapper.from(rq), linkedFlights);
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        mapper.applyFields(rq, existing, airline);
        mapper.mergeSlots(rq.getSlots(), existing, flightRepository::existsBySlot_SlotId);
        scheduleSlotBusinessRules.assertValidSlots(existing, ScheduleDraftMapper.slotsFrom(rq.getSlots()));
        flightHomeAirportRules.assertValidHomeRoute(existing, homeIata());
        Schedule saved = scheduleRepository.save(existing);
        publishLinkedFlights(linkedFlights);
        return enrichScheduleRs(mapper.toScheduleRs(saved));
    }

    @Transactional
    public void delete(Integer id) {
        Schedule schedule = loadSchedule(id);
        List<Flight> linked = flightRepository.findBySchedule_ScheduleId(id);
        if (!linked.isEmpty()) {
            String details = linked.stream()
                    .map(f -> "#%d %s, вылет %s".formatted(
                            f.getFlightId(),
                            f.getStatus(),
                            f.getScheduledDeparture().format(DEP_FMT)))
                    .collect(Collectors.joining("; "));
            throw new ConflictException(
                    "Нельзя удалить расписание %s: связано рейсов — %d (%s)"
                            .formatted(schedule.getFlightNumber(), linked.size(), details));
        }
        scheduleBusinessRules.assertMayDelete(false);
        scheduleRepository.deleteById(id);
    }

    private void publishLinkedFlights(List<Flight> linkedFlights) {
        for (Flight linked : linkedFlights) {
            Flight fresh = flightRepository.findById(linked.getFlightId()).orElseThrow();
            realtimeNotificationService.publishFlightUpdate(mapper.toFlightRsSummary(fresh));
        }
    }

    private Schedule loadSchedule(Integer id) {
        return scheduleRepository.findByIdWithSlots(id).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }

    private ScheduleRs enrichScheduleRs(ScheduleRs rs) {
        if (rs == null) {
            return null;
        }
        rs.setReactivationSuggested(shouldSuggestReactivation(rs));
        return rs;
    }

    private boolean shouldSuggestReactivation(ScheduleRs rs) {
        if (Boolean.TRUE.equals(rs.getIsActive())) {
            return false;
        }
        LocalDate effectiveTo = rs.getEffectiveTo();
        if (effectiveTo == null) {
            return false;
        }
        LocalDate today = airportClock.now().toLocalDate();
        return !effectiveTo.isBefore(today);
    }
}
