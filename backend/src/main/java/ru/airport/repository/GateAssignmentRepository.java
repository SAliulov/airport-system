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

    List<GateAssignment> findByFlight_FlightId(Integer flightId);

    List<GateAssignment> findByGate_GateId(Integer gateId);

    /**
     * Проверка занятости гейта: есть ли пересечение интервалов?
     *
     * Два интервала [A, B] и [C, D] пересекаются если: A < D AND B > C
     * То есть новый интервал [from, to] пересекается с существующим [assigned_from, assigned_to]
     * если: from < assigned_to AND to > assigned_from
     *
     * Параметр excludeFlightId нужен при изменении гейта существующего рейса —
     * чтобы не считать его собственное текущее назначение конфликтом.
     *
     * @param gateId          id гейта, который хотим назначить
     * @param from            начало нового интервала
     * @param to              конец нового интервала
     * @param excludeFlightId id рейса, назначение которого игнорируем (0 если нового рейса)
     * @return список конфликтующих назначений (пустой = гейт свободен)
     */
    @Query("""
            SELECT ga FROM GateAssignment ga
            WHERE ga.gate.gateId = :gateId
              AND ga.flight.flightId <> :excludeFlightId
              AND ga.assignedFrom < :to
              AND ga.assignedTo > :from
            """)
    List<GateAssignment> findOverlapping(
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
