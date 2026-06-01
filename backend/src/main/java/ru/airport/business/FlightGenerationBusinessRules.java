package ru.airport.business;

import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Генерация экземпляров рейсов по шаблонам за диапазон дат.
 */
public class FlightGenerationBusinessRules {

    private final SchedulePeriodicityBusinessRules periodicityRules;
    private final ScheduleOccurrenceBusinessRules occurrenceRules;

    public FlightGenerationBusinessRules(
            SchedulePeriodicityBusinessRules periodicityRules,
            ScheduleOccurrenceBusinessRules occurrenceRules) {
        this.periodicityRules = periodicityRules;
        this.occurrenceRules = occurrenceRules;
    }

    public record GenerationPlan(int skipped, List<Flight> toCreate) {
    }

    public GenerationPlan planGeneration(
            LocalDate fromDate,
            LocalDate toDate,
            List<Schedule> schedules,
            BiPredicate<Integer, LocalDate> flightExists) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate must be on or after fromDate");
        }

        int skipped = 0;
        List<Flight> toCreate = new ArrayList<>();

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            for (Schedule schedule : schedules) {
                if (!Boolean.TRUE.equals(schedule.getIsActive())) {
                    continue;
                }
                List<ScheduleSlot> slots = schedule.getSlots();
                if (slots == null) {
                    continue;
                }
                for (ScheduleSlot slot : slots) {
                    if (!periodicityRules.matchesOperationDate(schedule, slot, date)) {
                        continue;
                    }
                    if (flightExists.test(slot.getSlotId(), date)) {
                        skipped++;
                        continue;
                    }
                    toCreate.add(buildFlight(schedule, slot, date));
                }
            }
        }
        return new GenerationPlan(skipped, toCreate);
    }

    public Flight buildFlight(Schedule schedule, ScheduleSlot slot, LocalDate operationDate) {
        occurrenceRules.assertMatchesOperationDate(schedule, slot, operationDate);
        return Flight.builder()
                .schedule(schedule)
                .slot(slot)
                .operationDate(operationDate)
                .scheduledDeparture(occurrenceRules.computeScheduledDeparture(slot, operationDate))
                .scheduledArrival(occurrenceRules.computeScheduledArrival(slot, operationDate))
                .status(FlightStatus.SCHEDULED)
                .build();
    }
}
