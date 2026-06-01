package ru.airport.business;

import ru.airport.dto.ScheduleRq;
import ru.airport.exception.BadRequestException;
import ru.airport.exception.ConflictException;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.PeriodicityType;
import ru.airport.model.Schedule;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Правила шаблона расписания: сезон, периодичность, удаление, изменение при связанных рейсах.
 */
public class ScheduleBusinessRules {

    private static final String ROUTE_LOCKED_MESSAGE =
            "Нельзя менять маршрут или периодичность: есть рейс в статусе DEPARTED, ARRIVED или CANCELLED";

    public void assertMayDelete(boolean hasFlights) {
        if (hasFlights) {
            throw new ConflictException("Нельзя удалить расписание: есть связанные рейсы.");
        }
    }

    public void assertValidEffectiveRange(LocalDate effectiveFrom, LocalDate effectiveTo) {
        if (effectiveFrom == null) {
            throw new BadRequestException("Укажите дату начала действия шаблона (effectiveFrom)");
        }
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BadRequestException("Дата окончания не может быть раньше даты начала");
        }
    }

    public void assertValidPeriodicity(PeriodicityType type, Integer step) {
        if (type == null) {
            throw new BadRequestException("Укажите тип периодичности");
        }
        if (step == null || step < 1) {
            throw new BadRequestException("Шаг периодичности должен быть не меньше 1");
        }
    }

    /**
     * SCHEDULED/DELAYED — можно менять маршрут и слоты; при DEPARTED/ARRIVED/CANCELLED — только номер и АК.
     */
    public void assertMayUpdate(Schedule existing, ScheduleRq rq, List<Flight> linkedFlights) {
        assertValidEffectiveRange(rq.getEffectiveFrom(), rq.getEffectiveTo());
        assertValidPeriodicity(rq.getPeriodicityType(), rq.getPeriodicityStep());

        if (Boolean.FALSE.equals(rq.getIsActive())
                && Boolean.TRUE.equals(existing.getIsActive())
                && !linkedFlights.isEmpty()) {
            throw new ConflictException(
                    "Нельзя деактивировать шаблон: по нему уже созданы рейсы. Удалите рейсы или оставьте шаблон активным.");
        }

        boolean hasClosedFlight = linkedFlights.stream()
                .map(Flight::getStatus)
                .anyMatch(this::isRouteTimeLockedStatus);
        if (!hasClosedFlight) {
            return;
        }

        if (routeOrTemplateChanged(existing, rq)) {
            throw new ConflictException(ROUTE_LOCKED_MESSAGE);
        }
    }

    private boolean isRouteTimeLockedStatus(FlightStatus status) {
        return status == FlightStatus.DEPARTED
                || status == FlightStatus.ARRIVED
                || status == FlightStatus.CANCELLED;
    }

    private static boolean routeOrTemplateChanged(Schedule existing, ScheduleRq rq) {
        return !Objects.equals(normalize(existing.getOriginAirport()), normalize(rq.getOriginAirport()))
                || !Objects.equals(normalize(existing.getDestinationAirport()), normalize(rq.getDestinationAirport()))
                || !Objects.equals(existing.getEffectiveFrom(), rq.getEffectiveFrom())
                || !Objects.equals(existing.getEffectiveTo(), rq.getEffectiveTo())
                || existing.getPeriodicityType() != rq.getPeriodicityType()
                || !Objects.equals(existing.getPeriodicityStep(), rq.getPeriodicityStep());
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
