package ru.airport.business;

import ru.airport.config.AirportProperties;
import ru.airport.exception.BadRequestException;

import java.time.LocalDateTime;

/**
 * Валидация фактических времён рейса относительно плана, направления движения (Inbound/Outbound) и «сейчас».
 */
public class FlightActualTimeRules {

    private final AirportProperties.Scheduler config;

    public FlightActualTimeRules(AirportProperties airportProperties) {
        this.config = airportProperties.getScheduler();
    }

    /**
     * Проверка фактического времени вылета.
     */
    public void assertActualDeparture(
            LocalDateTime actualDeparture,
            LocalDateTime scheduledDeparture,
            FlightHomeAirportRules.OperationKind kind,
            LocalDateTime now) {
        if (actualDeparture == null) {
            return;
        }

        // Проверка близости к расписанию (мягкие границы)
        assertDepartureBounds(actualDeparture, scheduledDeparture, kind);
    }

    /**
     * Проверка фактического времени прилёта.
     */
    public void assertActualArrival(
            LocalDateTime actualArrival,
            LocalDateTime actualDeparture,
            LocalDateTime scheduledArrival,
            FlightHomeAirportRules.OperationKind kind,
            LocalDateTime now) {
        if (actualArrival == null) {
            return;
        }

        // Проверка близости к расписанию (мягкие границы)
        assertArrivalBounds(actualArrival, scheduledArrival, kind);

        // Хронологический инвариант: прилёт не может быть раньше вылета
        if (actualDeparture != null && actualArrival.isBefore(actualDeparture)) {
            throw new BadRequestException(
                    "Фактическое время прилёта не может быть раньше фактического времени вылета");
        }
    }

    /**
     * Валидация итоговых фактических времён после коррекции (merge) текущих значений рейса диспетчером.
     */
    public void assertCorrection(
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            FlightHomeAirportRules.OperationKind kind,
            LocalDateTime now) {
        assertActualDeparture(actualDeparture, scheduledDeparture, kind, now);
        assertActualArrival(actualArrival, actualDeparture, scheduledArrival, kind, now);
    }

    private void assertDepartureBounds(LocalDateTime actual, LocalDateTime scheduled, FlightHomeAirportRules.OperationKind kind) {
        if (actual == null || scheduled == null) {
            return;
        }

        // Здесь при необходимости можно сделать ветвление по kind (Inbound vs Outbound)
        // На данный момент применяем общие параметризованные границы, защищённые от магических чисел
        LocalDateTime earliestAllowed = scheduled.minusHours(config.getMaxDepartureEarlyHours());
        LocalDateTime latestAllowed = scheduled.plusHours(config.getMaxDepartureLateHours());

        if (actual.isBefore(earliestAllowed)) {
            throw new BadRequestException(String.format(
                    "Фактическое время вылета (%s) недопустимо. Не может быть более чем на %d ч. раньше планового (%s)",
                    actual, config.getMaxDepartureEarlyHours(), scheduled));
        }
        if (actual.isAfter(latestAllowed)) {
            throw new BadRequestException(String.format(
                    "Фактическое время вылета (%s) недопустимо. Не может быть более чем на %d ч. позже планового (%s)",
                    actual, config.getMaxDepartureLateHours(), scheduled));
        }
    }

    private void assertArrivalBounds(LocalDateTime actual, LocalDateTime scheduled, FlightHomeAirportRules.OperationKind kind) {
        if (actual == null || scheduled == null) {
            return;
        }

        LocalDateTime earliestAllowed = scheduled.minusHours(config.getMaxArrivalEarlyHours());
        LocalDateTime latestAllowed = scheduled.plusHours(config.getMaxArrivalLateHours());

        if (actual.isBefore(earliestAllowed)) {
            throw new BadRequestException(String.format(
                    "Фактическое время прилёта (%s) недопустимо. Не может быть более чем на %d ч. раньше планового (%s)",
                    actual, config.getMaxArrivalEarlyHours(), scheduled));
        }
        if (actual.isAfter(latestAllowed)) {
            throw new BadRequestException(String.format(
                    "Фактическое время прилёта (%s) недопустимо. Не может быть более чем на %d ч. позже планового (%s)",
                    actual, config.getMaxArrivalLateHours(), scheduled));
        }
    }

}