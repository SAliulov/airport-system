package ru.airport.business;

import ru.airport.exception.BadRequestException;

import java.time.LocalDateTime;

/**
 * Минимальная валидация фактических времён рейса (без жёсткого «не глубже N часов в прошлое»).
 */
public class FlightActualTimeRules {

    private final int maxFutureSkewMinutes;

    public FlightActualTimeRules(int maxFutureSkewMinutes) {
        this.maxFutureSkewMinutes = maxFutureSkewMinutes;
    }

    public void assertActualDeparture(LocalDateTime actualDeparture, LocalDateTime now) {
        if (actualDeparture == null) {
            return;
        }
        assertNotTooFarInFuture(actualDeparture, now, "вылета");
    }

    public void assertActualArrival(LocalDateTime actualArrival, LocalDateTime actualDeparture, LocalDateTime now) {
        if (actualArrival == null) {
            return;
        }
        assertNotTooFarInFuture(actualArrival, now, "прилёта");
        if (actualDeparture != null && actualArrival.isBefore(actualDeparture)) {
            throw new BadRequestException(
                    "Фактическое время прилёта не может быть раньше фактического времени вылета");
        }
    }

    private void assertNotTooFarInFuture(LocalDateTime value, LocalDateTime now, String label) {
        if (now == null) {
            return;
        }
        LocalDateTime maxAllowed = now.plusMinutes(maxFutureSkewMinutes);
        if (value.isAfter(maxAllowed)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть более чем на "
                            + maxFutureSkewMinutes + " мин в будущем");
        }
    }
}
