package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.GateAssignment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий назначений гейтов.
 *
 * Самый важный репозиторий с точки зрения бизнес-логики —
 * содержит запрос на проверку пересечения временных интервалов (задача 4).
 */
public interface GateAssignmentRepository extends JpaRepository<GateAssignment, Integer> {

    boolean existsByGate_GateId(Integer gateId);

    /**
     * Пересечения на гейте только с назначениями других рейсов (текущий рейс исключён).
     */
    @Query("""
            SELECT ga FROM GateAssignment ga
            WHERE ga.gate.gateId = :gateId
              AND ga.flight.flightId <> :excludeFlightId
              AND ga.assignedFrom < :to
              AND ga.assignedTo > :from
            """)
    List<GateAssignment> findOverlappingForOtherFlights(
            @Param("gateId") Integer gateId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("excludeFlightId") Integer excludeFlightId
    );

    /**
     * Все назначения гейтов за указанный день.
     * Используется для визуализации timeline (задача 7).
     */
    @Query("""
            SELECT ga FROM GateAssignment ga
            WHERE ga.assignedFrom < :dayEnd
              AND ga.assignedTo > :dayStart
            ORDER BY ga.gate.gateId, ga.assignedFrom
            """)
    List<GateAssignment> findByDay(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );
}
