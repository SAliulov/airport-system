package ru.airport.service.schedule;

import ru.airport.business.schedule.ScheduleDraft;
import ru.airport.business.schedule.ScheduleSlotDraft;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleSlotRq;

import java.util.List;

/** Маппинг REST DTO → domain draft для {@code business/}. */
public final class ScheduleDraftMapper {

    private ScheduleDraftMapper() {
    }

    public static ScheduleDraft from(ScheduleRq rq) {
        return new ScheduleDraft(
                rq.getFlightNumber(),
                rq.getOriginAirport(),
                rq.getDestinationAirport(),
                rq.getEffectiveFrom(),
                rq.getEffectiveTo(),
                rq.getIsActive(),
                rq.getPeriodicityType(),
                rq.getPeriodicityStep(),
                rq.getAirlineId()
        );
    }

    public static List<ScheduleSlotDraft> slotsFrom(List<ScheduleSlotRq> slots) {
        if (slots == null) {
            return List.of();
        }
        return slots.stream()
                .map(ScheduleDraftMapper::from)
                .toList();
    }

    public static ScheduleSlotDraft from(ScheduleSlotRq rq) {
        return new ScheduleSlotDraft(
                rq.getSlotId(),
                rq.getDayOfWeek(),
                rq.getDepartureTime(),
                rq.getArrivalTime()
        );
    }
}
