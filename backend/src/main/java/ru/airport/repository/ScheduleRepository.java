package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Schedule;

import java.time.LocalDate;
import java.util.List;

/**
 * Репозиторий шаблонов расписания.
 */
public interface ScheduleRepository extends JpaRepository<Schedule, Integer> {

    boolean existsByAirline_AirlineId(Integer airlineId);

    @Query("""
            SELECT DISTINCT s FROM Schedule s
            JOIN FETCH s.airline
            LEFT JOIN FETCH s.slots
            WHERE s.isActive = true
            ORDER BY s.flightNumber, s.scheduleId
            """)
    List<Schedule> findAllActiveWithSlots();

    @Query("""
            SELECT DISTINCT s FROM Schedule s
            JOIN FETCH s.airline
            LEFT JOIN FETCH s.slots
            WHERE s.scheduleId = :scheduleId
            """)
    List<Schedule> findByIdWithSlots(@Param("scheduleId") Integer scheduleId);

    @Query("""
            SELECT s FROM Schedule s
            WHERE s.isActive = true
              AND s.effectiveTo IS NOT NULL
              AND s.effectiveTo < :today
            """)
    List<Schedule> findActiveExpiredBefore(@Param("today") LocalDate today);
}
