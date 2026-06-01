package ru.airport.business;

import ru.airport.model.PeriodicityType;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Проверка, попадает ли дата под правила периодичности шаблона.
 */
public class SchedulePeriodicityBusinessRules {

    public boolean matchesOperationDate(Schedule schedule, ScheduleSlot slot, LocalDate targetDate) {
        if (schedule == null || slot == null || targetDate == null) {
            return false;
        }
        if (!Boolean.TRUE.equals(schedule.getIsActive())) {
            return false;
        }
        LocalDate effectiveFrom = schedule.getEffectiveFrom();
        if (effectiveFrom == null || targetDate.isBefore(effectiveFrom)) {
            return false;
        }
        LocalDate effectiveTo = schedule.getEffectiveTo();
        if (effectiveTo != null && targetDate.isAfter(effectiveTo)) {
            return false;
        }

        PeriodicityType type = schedule.getPeriodicityType();
        int step = schedule.getPeriodicityStep() != null ? schedule.getPeriodicityStep() : 1;

        if (type == PeriodicityType.WEEKLY) {
            Integer dow = slot.getDayOfWeek();
            if (dow == null || targetDate.getDayOfWeek().getValue() != dow) {
                return false;
            }
            long weeks = ChronoUnit.WEEKS.between(effectiveFrom, targetDate);
            return weeks % step == 0;
        }
        if (type == PeriodicityType.INTERVAL) {
            if (slot.getDayOfWeek() != null) {
                return false;
            }
            long days = ChronoUnit.DAYS.between(effectiveFrom, targetDate);
            return days % step == 0;
        }
        return false;
    }
}
