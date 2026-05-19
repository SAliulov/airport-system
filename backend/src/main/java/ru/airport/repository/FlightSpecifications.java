package ru.airport.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Динамические предикаты для списка рейсов API (без JPQL вида {@code (:p IS NULL OR ... )},
 * который на Hibernate 6 + PostgreSQL давал сбои).
 */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    /**
     * Фильтр по дню вылета ({@code schedule.scheduledDeparture}), статусу рейса, id авиакомпании.
     * Все параметры опциональны; пустой набор предикатов — все рейсы (с теми же join, что и раньше).
     */
    public static Specification<Flight> forApiList(
            LocalDateTime dayStart,
            LocalDateTime dayEnd,
            FlightStatus status,
            Integer airlineId,
            String flightNumberQuery) {
        return (root, query, cb) -> {
            // Только join (без fetch): fetch в Specification + DISTINCT даёт на PostgreSQL
            // ошибку вида «ORDER BY выражения должны входить в SELECT при DISTINCT» и 500 на API.
            // Связи schedule/airline подгружаются через join; aircraftType — лениво в той же транзакции.
            root.join("aircraftType", JoinType.LEFT);
            Join<Flight, Schedule> scheduleJoin = root.join("schedule", JoinType.INNER);
            Join<Schedule, Airline> airlineJoin = scheduleJoin.join("airline", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            if (dayStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(scheduleJoin.get("scheduledDeparture"), dayStart));
            }
            if (dayEnd != null) {
                predicates.add(cb.lessThan(scheduleJoin.get("scheduledDeparture"), dayEnd));
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

            query.orderBy(cb.asc(scheduleJoin.get("scheduledDeparture")));
            // DISTINCT не используем: при inner join schedule+airline дубликатов Flight не будет.

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
