package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public List<ScheduleRs> list(LocalDate date, Integer airlineId, FlightStatus status, String search) {
        List<Schedule> candidates = resolveCandidates(date, status);
        String q = search != null ? search.trim() : "";
        return candidates.stream()
                .filter(s -> airlineId == null || s.getAirline().getAirlineId().equals(airlineId))
                .filter(s -> q.isEmpty() || s.getFlightNumber().toLowerCase().contains(q.toLowerCase()))
                .sorted(Comparator.comparing(Schedule::getScheduledDeparture))
                .map(mapper::toScheduleRs)
                .toList();
    }

    private List<Schedule> resolveCandidates(LocalDate date, FlightStatus status) {
        if (date != null) {
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            if (status != null) {
                List<Flight> flights = flightRepository.findByScheduleDayAndOptionalStatus(start, end, status);
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
        scheduleRepository.delete(loadSchedule(id));
    }

    private Schedule loadSchedule(Integer id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", id));
    }
}
