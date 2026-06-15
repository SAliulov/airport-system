package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Репозиторий конкретных выполняемых рейсов.
 */
public interface FlightRepository extends JpaRepository<Flight, Integer>, JpaSpecificationExecutor<Flight> {

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
            JOIN FETCH f.slot sl
            LEFT JOIN FETCH f.aircraftType
            LEFT JOIN FETCH f.gateAssignments ga
            LEFT JOIN FETCH ga.gate
            WHERE f.flightId = :flightId
            """)
    Optional<Flight> findByIdForDetail(@Param("flightId") Integer flightId);

    @Query("""
            SELECT DISTINCT f FROM Flight f
            JOIN FETCH f.schedule s
            JOIN FETCH f.slot sl
            LEFT JOIN FETCH f.gateAssignments
            LEFT JOIN FETCH f.delayWarnings
            WHERE s.scheduleId = :scheduleId
            """)
    List<Flight> findBySchedule_ScheduleIdWithCollections(@Param("scheduleId") Integer scheduleId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Flight f WHERE f.schedule.scheduleId = :scheduleId")
    int deleteByScheduleIdBulk(@Param("scheduleId") Integer scheduleId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Flight f WHERE f.flightId IN :flightIds")
    int deleteByFlightIdsBulk(@Param("flightIds") List<Integer> flightIds);

    @Query("""
            SELECT CAST(f.slot.slotId AS string) || '|' || CAST(f.operationDate AS string)
            FROM Flight f
            WHERE f.operationDate BETWEEN :from AND :to
            """)
    Set<String> findSlotDateKeysInRange(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);
}
