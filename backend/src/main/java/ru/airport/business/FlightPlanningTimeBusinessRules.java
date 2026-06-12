package ru.airport.business;

import ru.airport.exception.BadRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Инварианты планового времени: новые рейсы и новые интервалы ресурсов не создаются задним числом.
 */
public class FlightPlanningTimeBusinessRules {

    public void assertGenerationStartsTodayOrLater(LocalDate fromDate, LocalDate today) {
        if (fromDate != null && today != null && fromDate.isBefore(today)) {
            throw new BadRequestException(
                    "Период генерации не может начинаться раньше текущего дня аэропорта (" + today + ")");
        }
    }

    public void assertScheduledDepartureInFuture(LocalDateTime scheduledDeparture, LocalDateTime now) {
        if (!isScheduledDepartureInFuture(scheduledDeparture, now)) {
            throw new BadRequestException(
                    "Нельзя создать рейс: плановый вылет уже прошёл по времени аэропорта (" + now + ")");
        }
    }

    public boolean isScheduledDepartureInFuture(LocalDateTime scheduledDeparture, LocalDateTime now) {
        return scheduledDeparture != null && now != null && scheduledDeparture.isAfter(now);
    }
}
