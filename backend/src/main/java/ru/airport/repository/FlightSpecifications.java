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
 * Динамические предикаты для списка рейсов API.
 */
public final class FlightSpecifications {

    private FlightSpecifications() {
    }

    public static Specification<Flight> forApiList(
            LocalDateTime dayStart,
            LocalDateTime dayEnd,
            FlightStatus status,
            Integer airlineId,
            String flightNumberQuery) {
        return (root, query, cb) -> {
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

            query.orderBy(
                    cb.asc(root.get("scheduledDeparture")),
                    cb.asc(root.get("flightId")));

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
