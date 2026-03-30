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
            Integer airlineId) {
        return (root, query, cb) -> {
            root.fetch("aircraftType", JoinType.LEFT);
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

            query.orderBy(cb.asc(scheduleJoin.get("scheduledDeparture")));
            query.distinct(true);

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
