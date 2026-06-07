package ru.airport.business.schedule;

import ru.airport.model.PeriodicityType;

import java.time.LocalDate;

/**
 * Доменное представление шаблона расписания для business-правил (без DTO/Jackson).
 */
public record ScheduleDraft(
        String flightNumber,
        String originAirport,
        String destinationAirport,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean isActive,
        PeriodicityType periodicityType,
        Integer periodicityStep,
        Integer airlineId
) {
}
