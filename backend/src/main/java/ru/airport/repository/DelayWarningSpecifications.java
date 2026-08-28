package ru.airport.repository;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import ru.airport.model.Airline;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Динамические предикаты для сводного списка предупреждений о задержках (вкладка «Задержки»).
 */
public final class DelayWarningSpecifications {

    private DelayWarningSpecifications() {
    }

    /**
     * @param dayStart/dayEnd   границы дня по {@code flight.scheduledDeparture}, или null
     * @param airlineId         фильтр по авиакомпании, или null
     * @param flightNumberQuery подстрока номера рейса (LIKE, lower case), или null
     */
    public static Specification<DelayWarning> forManagementList(
            LocalDateTime dayStart,
            LocalDateTime dayEnd,
            Integer airlineId,
            String flightNumberQuery) {
        return (root, query, cb) -> {
            Fetch<DelayWarning, Flight> flightFetch = root.fetch("flight", JoinType.INNER);
            Join<DelayWarning, Flight> flightJoin = (Join<DelayWarning, Flight>) flightFetch;
            Fetch<Flight, Schedule> scheduleFetch = flightJoin.fetch("schedule", JoinType.INNER);
            Join<Flight, Schedule> scheduleJoin = (Join<Flight, Schedule>) scheduleFetch;
            Join<Schedule, Airline> airlineJoin = scheduleJoin.join("airline", JoinType.INNER);

            List<Predicate> predicates = new ArrayList<>();
            if (dayStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(flightJoin.get("scheduledDeparture"), dayStart));
            }
            if (dayEnd != null) {
                predicates.add(cb.lessThan(flightJoin.get("scheduledDeparture"), dayEnd));
            }
            if (airlineId != null) {
                predicates.add(cb.equal(airlineJoin.get("airlineId"), airlineId));
            }
            if (flightNumberQuery != null) {
                predicates.add(cb.like(
                        cb.lower(scheduleJoin.get("flightNumber")),
                        "%" + flightNumberQuery.toLowerCase() + "%"));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
