package ru.airport.business;

import ru.airport.exception.BadRequestException;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Расчёт плановых времён экземпляра рейса по слоту и дате операции.
 */
public class ScheduleOccurrenceBusinessRules {

    private final SchedulePeriodicityBusinessRules periodicityRules;

    public ScheduleOccurrenceBusinessRules(SchedulePeriodicityBusinessRules periodicityRules) {
        this.periodicityRules = periodicityRules;
    }

    public void assertMatchesOperationDate(Schedule schedule, ScheduleSlot slot, LocalDate operationDate) {
        if (!periodicityRules.matchesOperationDate(schedule, slot, operationDate)) {
            throw new BadRequestException(
                    "Дата %s не соответствует правилам периодичности шаблона расписания"
                            .formatted(operationDate));
        }
    }

    public LocalDateTime computeScheduledDeparture(ScheduleSlot slot, LocalDate operationDate) {
        return LocalDateTime.of(operationDate, slot.getDepartureTime());
    }

    public LocalDateTime computeScheduledArrival(ScheduleSlot slot, LocalDate operationDate) {
        LocalTime dep = slot.getDepartureTime();
        LocalTime arr = slot.getArrivalTime();
        LocalDate arrivalDate = operationDate;
        if (arr != null && dep != null && !arr.isAfter(dep)) {
            arrivalDate = operationDate.plusDays(1);
        }
        return LocalDateTime.of(arrivalDate, arr);
    }

    public void assertValidSlotTimes(LocalTime departureTime, LocalTime arrivalTime) {
        if (departureTime == null || arrivalTime == null) {
            throw new BadRequestException("Укажите время вылета и прилёта слота");
        }
        if (departureTime.equals(arrivalTime)) {
            throw new BadRequestException("Время вылета и прилёта слота не могут совпадать");
        }
    }
}
