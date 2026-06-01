package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий конкретных выполняемых рейсов.
 */
public interface FlightRepository extends JpaRepository<Flight, Integer>, JpaSpecificationExecutor<Flight> {

    boolean existsBySchedule_ScheduleId(Integer scheduleId);

    boolean existsBySlot_SlotId(Integer slotId);

    boolean existsBySlot_SlotIdAndOperationDate(Integer slotId, LocalDate operationDate);

    boolean existsByAircraftType_AircraftTypeId(Integer aircraftTypeId);

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findBySchedule_ScheduleId(Integer scheduleId);

    @Query("""
            SELECT f FROM Flight f
            WHERE f.scheduledDeparture >= :dayStart
              AND f.scheduledDeparture < :dayEnd
            ORDER BY f.scheduledDeparture
            """)
    List<Flight> findByDay(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.schedule s
            JOIN FETCH f.slot sl
            LEFT JOIN FETCH f.aircraftType
            WHERE f.status IN (:statuses)
            """)
    List<Flight> findForAutoStatusProcessing(@Param("statuses") Collection<FlightStatus> statuses);

    @Query("""
            SELECT f FROM Flight f
            JOIN FETCH f.schedule s
            JOIN FETCH f.slot sl
            LEFT JOIN FETCH f.aircraftType
            LEFT JOIN FETCH f.gateAssignments
            WHERE f.flightId = :flightId
            """)
    Optional<Flight> findByIdForAutoProcessing(@Param("flightId") Integer flightId);

    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.schedule s
            JOIN FETCH s.airline a
            JOIN FETCH f.slot sl
            LEFT JOIN FETCH f.aircraftType
            ORDER BY f.scheduledDeparture ASC, f.flightId ASC
            """)
    List<Flight> findAllForApiList();
}
