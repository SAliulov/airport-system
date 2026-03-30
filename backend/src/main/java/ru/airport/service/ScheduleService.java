package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * @param direction IATA аэропорта: вылет или прилёт совпадает с кодом (FirstLab §2, задача 2).
     */
    public List<ScheduleRs> list(
            LocalDate date,
            Integer airlineId,
            FlightStatus status,
            String search,
            String direction
    ) {
        List<Schedule> candidates = resolveCandidates(date, status);
        String q = search != null ? search.trim() : "";
        String dir = normalizeAirport(direction);
        return candidates.stream()
                .filter(s -> airlineId == null || s.getAirline().getAirlineId().equals(airlineId))
                .filter(s -> q.isEmpty() || s.getFlightNumber().toLowerCase().contains(q.toLowerCase()))
                .filter(s -> dir == null || matchesDirection(s, dir))
                .sorted(Comparator.comparing(Schedule::getScheduledDeparture))
                .map(mapper::toScheduleRs)
                .toList();
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
                        FlightSpecifications.forApiList(start, end, status, null));
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
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        Schedule saved = scheduleRepository.save(mapper.newSchedule(rq, airline));
        return mapper.toScheduleRs(saved);
    }

    @Transactional
    public ScheduleRs update(Integer id, ScheduleRq rq) {
        Schedule s = loadSchedule(id);
        Airline airline = airlineRepository.findById(rq.getAirlineId())
                .orElseThrow(() -> new ResourceNotFoundException("Airline", rq.getAirlineId()));
        mapper.apply(rq, s, airline);
        return mapper.toScheduleRs(scheduleRepository.save(s));
    }

    @Transactional
    public void delete(Integer id) {
        loadSchedule(id);
        if (flightRepository.existsBySchedule_ScheduleId(id)) {
            throw new ConflictException("Нельзя удалить расписание: есть связанные рейсы.");
        }
        scheduleRepository.deleteById(id);
    }

    private Schedule loadSchedule(Integer id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }
}
