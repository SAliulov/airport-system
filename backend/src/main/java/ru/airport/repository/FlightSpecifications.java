package ru.airport.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Динамические предикаты для списка рейсов API.
 */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    /**
     * Сборка предикатов для {@code GET /flights/filter} и {@code /flights/search}.
     *
     * @param dayStart/dayEnd         границы операционного дня (scheduledDeparture)
     * @param status                  фильтр по статусу или null
     * @param airlineId               фильтр по авиакомпании или null
     * @param flightNumberQuery       подстрока номера рейса (LIKE, lower case) или null
     * @param terminal                активный гейт рейса в терминале или null
     * @param hourWindowStart/hourWindowEnd дополнительное окно по scheduledDeparture или null
     * @param originIata              фильтр по аэропорту отправления или null
     * @param destinationIata         фильтр по аэропорту назначения или null
     */
    public static Specification<Flight> forApiList(
            LocalDateTime dayStart,
            LocalDateTime dayEnd,
            FlightStatus status,
            Integer airlineId,
            String flightNumberQuery,
            String terminal,
            LocalDateTime hourWindowStart,
            LocalDateTime hourWindowEnd,
            String originIata,
            String destinationIata) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            root.join("aircraftType", JoinType.LEFT);
            Join<Flight, Schedule> scheduleJoin = root.join("schedule", JoinType.INNER);
            Join<Schedule, Airline> airlineJoin = scheduleJoin.join("airline", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            if (dayStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDeparture"), dayStart));
            }
            if (dayEnd != null) {
                predicates.add(cb.lessThan(root.get("scheduledDeparture"), dayEnd));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (airlineId != null) {
                predicates.add(cb.equal(airlineJoin.get("airlineId"), airlineId));
            }
            if (flightNumberQuery != null && !flightNumberQuery.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(scheduleJoin.get("flightNumber")),
                        "%" + flightNumberQuery.trim().toLowerCase() + "%"));
            }
            if (hourWindowStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDeparture"), hourWindowStart));
            }
            if (hourWindowEnd != null) {
                predicates.add(cb.lessThan(root.get("scheduledDeparture"), hourWindowEnd));
            }
            if (terminal != null && !terminal.isBlank()) {
                predicates.add(activeGateTerminalEquals(root, query, cb, terminal.trim()));
            }
            if (originIata != null) {
                predicates.add(cb.equal(
                        cb.upper(cb.trim(scheduleJoin.get("originAirport"))), originIata));
            }
            if (destinationIata != null) {
                predicates.add(cb.equal(
                        cb.upper(cb.trim(scheduleJoin.get("destinationAirport"))), destinationIata));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** EXISTS: у рейса активное (max assignment_id) назначение на гейт с заданным terminal. */
    private static Predicate activeGateTerminalEquals(
            Root<Flight> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String terminal) {
        Subquery<Integer> maxAssignSub = query.subquery(Integer.class);
        Root<GateAssignment> gaMax = maxAssignSub.from(GateAssignment.class);
        maxAssignSub.select(cb.max(gaMax.get("assignmentId")))
                .where(cb.equal(gaMax.get("flight"), root));

        Subquery<Long> exists = query.subquery(Long.class);
        Root<GateAssignment> ga = exists.from(GateAssignment.class);
        Join<GateAssignment, Gate> gateJoin = ga.join("gate", JoinType.INNER);
        exists.select(cb.literal(1L))
                .where(
                        cb.equal(ga.get("flight"), root),
                        cb.equal(ga.get("assignmentId"), maxAssignSub),
                        cb.equal(gateJoin.get("terminal"), terminal));
        return cb.exists(exists);
    }
}
