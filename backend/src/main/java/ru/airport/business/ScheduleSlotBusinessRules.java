package ru.airport.business;

import ru.airport.dto.ScheduleSlotRq;
import ru.airport.exception.BadRequestException;
import ru.airport.model.PeriodicityType;
import ru.airport.model.Schedule;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Валидация слотов шаблона расписания при создании и обновлении.
 */
public class ScheduleSlotBusinessRules {

    private final ScheduleOccurrenceBusinessRules occurrenceRules;

    public ScheduleSlotBusinessRules(ScheduleOccurrenceBusinessRules occurrenceRules) {
        this.occurrenceRules = occurrenceRules;
    }

    public void assertValidSlots(Schedule schedule, List<ScheduleSlotRq> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new BadRequestException("Укажите хотя бы один слот расписания");
        }
        PeriodicityType type = schedule.getPeriodicityType();
        if (type == PeriodicityType.INTERVAL) {
            if (slots.size() != 1) {
                throw new BadRequestException("Для INTERVAL допускается ровно один слот без дня недели");
            }
            ScheduleSlotRq slot = slots.getFirst();
            if (slot.getDayOfWeek() != null) {
                throw new BadRequestException("Для INTERVAL день недели слота должен быть пустым");
            }
            occurrenceRules.assertValidSlotTimes(slot.getDepartureTime(), slot.getArrivalTime());
            assertSlotFitsEffectivePeriod(schedule, null);
            return;
        }

        Set<Integer> seenDow = new HashSet<>();
        for (ScheduleSlotRq slot : slots) {
            Integer dow = slot.getDayOfWeek();
            if (dow == null || dow < 1 || dow > 7) {
                throw new BadRequestException("Для WEEKLY укажите день недели слота от 1 (Пн) до 7 (Вс)");
            }
            if (!seenDow.add(dow)) {
                throw new BadRequestException("День недели %d указан более одного раза".formatted(dow));
            }
            occurrenceRules.assertValidSlotTimes(slot.getDepartureTime(), slot.getArrivalTime());
            assertSlotFitsEffectivePeriod(schedule, dow);
        }
    }

    /**
     * Сценарий A: DOW или INTERVAL-якорь не попадает в конечный период действия шаблона.
     */
    public void assertSlotFitsEffectivePeriod(Schedule schedule, Integer dayOfWeek) {
        LocalDate effectiveFrom = schedule.getEffectiveFrom();
        LocalDate effectiveTo = schedule.getEffectiveTo();
        if (effectiveFrom == null || effectiveTo == null) {
            return;
        }

        PeriodicityType type = schedule.getPeriodicityType();
        int step = schedule.getPeriodicityStep() != null ? schedule.getPeriodicityStep() : 1;

        if (type == PeriodicityType.WEEKLY) {
            if (dayOfWeek == null) {
                return;
            }
            LocalDate first = effectiveFrom;
            while (first.getDayOfWeek().getValue() != dayOfWeek) {
                first = first.plusDays(1);
            }
            if (first.isAfter(effectiveTo)) {
                throw new BadRequestException(
                        "Слот для дня недели %d не попадает в короткий период действия расписания (с %s по %s)"
                                .formatted(dayOfWeek, effectiveFrom, effectiveTo));
            }
            return;
        }

        if (type == PeriodicityType.INTERVAL) {
            for (LocalDate d = effectiveFrom; !d.isAfter(effectiveTo); d = d.plusDays(1)) {
                if (ChronoUnit.DAYS.between(effectiveFrom, d) % step == 0) {
                    return;
                }
            }
            throw new BadRequestException(
                    "Интервальный слот не попадает в короткий период действия расписания (с %s по %s)"
                            .formatted(effectiveFrom, effectiveTo));
        }
    }
}
