package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Репозиторий конкретных выполняемых рейсов.
 * Центральный репозиторий системы.
 */
public interface FlightRepository extends JpaRepository<Flight, Integer> {

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findBySchedule_ScheduleId(Integer scheduleId);

    /**
     * Рейсы по статусу с плановым вылетом в указанный день.
     * Используется для фильтрации расписания (задача 2).
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN f.schedule s
            WHERE s.scheduledDeparture >= :dayStart
              AND s.scheduledDeparture < :dayEnd
            ORDER BY s.scheduledDeparture
            """)
    List<Flight> findByDay(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    /**
     * Рейсы со статусом SCHEDULED или DELAYED,
     * у которых плановое время вылета уже наступило.
     * Используется планировщиком для автообновления статуса (задача 3).
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN f.schedule s
            WHERE f.status IN (:departureStatuses)
              AND s.scheduledDeparture <= :now
            """)
    List<Flight> findReadyToDeparture(
            @Param("now") LocalDateTime now,
            @Param("departureStatuses") Collection<FlightStatus> departureStatuses);

    default List<Flight> findReadyToDeparture(LocalDateTime now) {
        return findReadyToDeparture(now, EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED));
    }

    /**
     * Рейсы со статусом DEPARTED,
     * у которых плановое время прилёта уже наступило.
     * Используется планировщиком для автообновления статуса (задача 3).
     */
    @Query("""
            SELECT f FROM Flight f
            JOIN f.schedule s
            WHERE f.status = :status
              AND s.scheduledArrival <= :now
            """)
    List<Flight> findReadyToArrive(
            @Param("now") LocalDateTime now,
            @Param("status") FlightStatus status);

    default List<Flight> findReadyToArrive(LocalDateTime now) {
        return findReadyToArrive(now, FlightStatus.DEPARTED);
    }

    /**
     * Рейсы, у которых плановый вылет в указанные сутки; опционально по статусу.
     * Для {@code GET /flights} и фильтрации расписания (задача 2).
     */
    @Query("""
            SELECT f FROM Flight f JOIN f.schedule s
            WHERE s.scheduledDeparture >= :dayStart
              AND s.scheduledDeparture < :dayEnd
              AND (:status IS NULL OR f.status = :status)
            ORDER BY s.scheduledDeparture
            """)
    List<Flight> findByScheduleDayAndOptionalStatus(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd,
            @Param("status") FlightStatus status
    );
}
