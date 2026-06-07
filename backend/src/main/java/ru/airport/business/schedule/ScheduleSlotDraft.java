package ru.airport.business.schedule;

import java.time.LocalTime;

/**
 * Доменное представление слота шаблона для business-правил (без DTO).
 */
public record ScheduleSlotDraft(
        Integer slotId,
        Integer dayOfWeek,
        LocalTime departureTime,
        LocalTime arrivalTime
) {
}
