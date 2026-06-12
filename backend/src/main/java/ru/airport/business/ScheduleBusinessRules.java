package ru.airport.business;

import ru.airport.business.schedule.ScheduleDraft;
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

    private static final String LINKED_TEMPLATE_LOCKED_MESSAGE =
            "Нельзя менять маршрут или периодичность: по шаблону уже созданы рейсы. "
                    + "Удалите рейсы по шаблону и сгенерируйте их заново.";

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
    public void assertMayUpdate(Schedule existing, ScheduleDraft draft, List<Flight> linkedFlights) {
        assertValidEffectiveRange(draft.effectiveFrom(), draft.effectiveTo());
        assertValidPeriodicity(draft.periodicityType(), draft.periodicityStep());

        if (Boolean.FALSE.equals(draft.isActive())
                && Boolean.TRUE.equals(existing.getIsActive())
                && hasOpenOperationalFlight(linkedFlights)) {
            throw new ConflictException(
                    "Нельзя деактивировать шаблон: есть рейсы в статусе SCHEDULED, DEPARTED или DELAYED.");
        }

        if (!linkedFlights.isEmpty() && routeOrTemplateChanged(existing, draft)) {
            throw new ConflictException(LINKED_TEMPLATE_LOCKED_MESSAGE);
        }
    }

    private boolean hasOpenOperationalFlight(List<Flight> linkedFlights) {
        return linkedFlights.stream()
                .map(Flight::getStatus)
                .anyMatch(this::isOpenOperationalStatus);
    }

    private boolean isOpenOperationalStatus(FlightStatus status) {
        return status == FlightStatus.SCHEDULED
                || status == FlightStatus.DEPARTED
                || status == FlightStatus.DELAYED;
    }

    private static boolean routeOrTemplateChanged(Schedule existing, ScheduleDraft draft) {
        if (!Objects.equals(normalize(existing.getOriginAirport()), normalize(draft.originAirport()))) {
            return true;
        }
        if (!Objects.equals(normalize(existing.getDestinationAirport()), normalize(draft.destinationAirport()))) {
            return true;
        }
        if (!Objects.equals(existing.getEffectiveFrom(), draft.effectiveFrom())) {
            return true;
        }
        if (existing.getPeriodicityType() != draft.periodicityType()) {
            return true;
        }
        if (!Objects.equals(existing.getPeriodicityStep(), draft.periodicityStep())) {
            return true;
        }
        return effectiveToShortened(existing.getEffectiveTo(), draft.effectiveTo());
    }

    /** Запрещено только сокращение периода действия; удлинение effectiveTo допустимо. */
    private static boolean effectiveToShortened(LocalDate existingTo, LocalDate requestedTo) {
        if (Objects.equals(existingTo, requestedTo)) {
            return false;
        }
        if (existingTo == null) {
            return false;
        }
        if (requestedTo == null) {
            return false;
        }
        return requestedTo.isBefore(existingTo);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
