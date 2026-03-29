package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий планового расписания рейсов.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {

    List<Schedule> findByAirline_AirlineId(Integer airlineId);

    List<Schedule> findByFlightNumberContainingIgnoreCase(String flightNumber);

    /**
     * Расписания, у которых плановый вылет попадает в указанный день.
     * Используется для фильтрации расписания на день (задача 2, задача 6).
     */
    @Query("""
            SELECT s FROM Schedule s
            WHERE s.scheduledDeparture >= :dayStart
              AND s.scheduledDeparture < :dayEnd
            ORDER BY s.scheduledDeparture
            """)
    List<Schedule> findByDay(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Расписания по направлению (аэропорт вылета или прилёта).
     * Используется для фильтрации в табло.
     */
    List<Schedule> findByOriginAirportOrDestinationAirport(
            String originAirport,
            String destinationAirport
    );
}
