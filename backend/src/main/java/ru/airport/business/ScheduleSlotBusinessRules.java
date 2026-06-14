package ru.airport.business;

import ru.airport.business.schedule.ScheduleSlotDraft;
import ru.airport.exception.BadRequestException;
import ru.airport.model.PeriodicityType;
import ru.airport.model.Schedule;

import java.time.LocalDate;
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

    public void assertValidSlots(Schedule schedule, List<ScheduleSlotDraft> slots) {
        if (slots == null || slots.isEmpty()) {
            throw new BadRequestException("Укажите хотя бы один слот расписания");
        }
        PeriodicityType type = schedule.getPeriodicityType();
        if (type == PeriodicityType.INTERVAL) {
            if (slots.size() != 1) {
                throw new BadRequestException("Для INTERVAL допускается ровно один слот без дня недели");
            }
            ScheduleSlotDraft slot = slots.getFirst();
            if (slot.dayOfWeek() != null) {
                throw new BadRequestException("Для INTERVAL день недели слота должен быть пустым");
            }
            occurrenceRules.assertValidSlotTimes(slot.departureTime(), slot.arrivalTime());
            assertSlotFitsEffectivePeriod(schedule, null);
            return;
        }

        Set<Integer> seenDow = new HashSet<>();
        for (ScheduleSlotDraft slot : slots) {
            Integer dow = slot.dayOfWeek();
            if (dow == null || dow < 1 || dow > 7) {
                throw new BadRequestException("Для WEEKLY укажите день недели слота от 1 (Пн) до 7 (Вс)");
            }
            if (!seenDow.add(dow)) {
                throw new BadRequestException("День недели %d указан более одного раза".formatted(dow));
            }
            occurrenceRules.assertValidSlotTimes(slot.departureTime(), slot.arrivalTime());
            assertSlotFitsEffectivePeriod(schedule, dow);
        }
    }

    /**
     * Сценарий A: DOW или INTERVAL-якорь не попадает в конечный период действия шаблона.
     */
    public void assertSlotFitsEffectivePeriod(Schedule schedule, Integer dayOfWeek) {
        LocalDate effectiveFrom = schedule.getEffectiveFrom();
        LocalDate effectiveTo = schedule.getEffectiveTo();
        if (effectiveFrom == null) {
            return;
        }

        // Открытое расписание (без даты окончания) — никаких ограничений на DOW
        if (effectiveTo == null) {
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
                        "Слот для дня недели %d не попадает в период действия расписания (с %s по %s)"
                                .formatted(dayOfWeek, effectiveFrom, effectiveTo));
            }
            return;
        }

        if (type == PeriodicityType.INTERVAL) {
            for (LocalDate d = effectiveFrom; !d.isAfter(effectiveTo); d = d.plusDays(step)) {
                return;
            }
            throw new BadRequestException(
                    "Интервальный слот не попадает в период действия расписания (с %s по %s)"
                            .formatted(effectiveFrom, effectiveTo));
        }
    }
}
