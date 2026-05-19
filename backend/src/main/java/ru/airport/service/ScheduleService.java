package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.ScheduleBusinessRules;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleRs;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final FlightRepository flightRepository;
    private final AirlineRepository airlineRepository;
    private final DtoMapper mapper;
    private final ScheduleBusinessRules scheduleBusinessRules;

    @Value("${airport.home-iata}")
    private String homeIata;

    /**
     * Все расписания без фильтров (GET /schedules).
     */
    public List<ScheduleRs> listAll() {
        return scheduleRepository.findAll(Sort.by(Sort.Order.asc("scheduledDeparture"))).stream()
                .map(mapper::toScheduleRs)
                .toList();
    }

    /**
     * Фильтрация по дате, авиакомпании, статусу рейса, направлению (GET /schedules/filter).
     *
     * @param statusRaw значение query {@code status} (имя enum), парсится здесь.
     */
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

    /**
     * Поиск по подстроке номера рейса с опциональными фильтрами (GET /schedules/search).
     *
     * @param query     подстрока для поиска в номере рейса (обязательно)
     * @param statusRaw значение query {@code status} (имя enum), парсится здесь.
     */
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
                .sorted(Comparator.comparing(Schedule::getScheduledDeparture))
                .map(mapper::toScheduleRs)
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
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            if (status != null) {
                List<Flight> flights = flightRepository.findAll(
                        FlightSpecifications.forApiList(start, end, status, null, null));
                return distinctSchedules(flights.stream().map(Flight::getSchedule).toList());
            }
            return scheduleRepository.findByDay(start, end);
        }
        if (status != null) {
            List<Flight> flights = flightRepository.findByStatus(status);
            List<Schedule> list = flights.stream().map(Flight::getSchedule).toList();
            return distinctSchedules(list).stream()
                    .sorted(Comparator.comparing(Schedule::getScheduledDeparture))
                    .toList();
        }
        return scheduleRepository.findAll(Sort.by(Sort.Order.asc("scheduledDeparture")));
    }

    private static List<Schedule> distinctSchedules(List<Schedule> schedules) {
        Map<Integer, Schedule> map = new LinkedHashMap<>();
        for (Schedule s : schedules) {
            map.putIfAbsent(s.getScheduleId(), s);
        }
        return new ArrayList<>(map.values());
    }

    public ScheduleRs getById(Integer id) {
        return mapper.toScheduleRs(loadSchedule(id));
    }

    @Transactional
    public ScheduleRs create(ScheduleRq rq) {
        TextNormalization.normalizeScheduleAirports(rq);
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        Schedule draft = mapper.newSchedule(rq, airline);
        FlightHomeAirportRules.assertScheduleIncludesHome(draft, homeIata);
        Schedule saved = scheduleRepository.save(draft);
        return mapper.toScheduleRs(saved);
    }

    @Transactional
    public ScheduleRs update(Integer id, ScheduleRq rq) {
        TextNormalization.normalizeScheduleAirports(rq);
        Schedule s = loadSchedule(id);
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        mapper.apply(rq, s, airline);
        FlightHomeAirportRules.assertScheduleIncludesHome(s, homeIata);
        return mapper.toScheduleRs(scheduleRepository.save(s));
    }

    @Transactional
    public void delete(Integer id) {
        loadSchedule(id);
        scheduleBusinessRules.assertMayDelete(flightRepository.existsBySchedule_ScheduleId(id));
        scheduleRepository.deleteById(id);
    }

    private Schedule loadSchedule(Integer id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }
}
