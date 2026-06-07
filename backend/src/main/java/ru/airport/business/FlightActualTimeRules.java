package ru.airport.business;

import ru.airport.exception.BadRequestException;

import java.time.LocalDateTime;

/**
 * Валидация фактических времён рейса относительно плана и «сейчас».
 */
public class FlightActualTimeRules {

    private static final int SCHEDULED_FUTURE_LEAD_MINUTES = 15;

    private final int maxFutureSkewMinutes;

    public FlightActualTimeRules(int maxFutureSkewMinutes) {
        this.maxFutureSkewMinutes = maxFutureSkewMinutes;
    }

    public void assertActualDeparture(
            LocalDateTime actualDeparture,
            LocalDateTime scheduledDeparture,
            LocalDateTime now) {
        if (actualDeparture == null) {
            return;
        }
        assertNotTooFarInFuture(actualDeparture, futureReference(now, scheduledDeparture), "вылета");
        assertSoftBounds(actualDeparture, scheduledDeparture, "вылета");
    }

    public void assertActualArrival(
            LocalDateTime actualArrival,
            LocalDateTime actualDeparture,
            LocalDateTime scheduledArrival,
            LocalDateTime now) {
        if (actualArrival == null) {
            return;
        }
        assertNotTooFarInFuture(actualArrival, futureReference(now, scheduledArrival), "прилёта");
        assertSoftBounds(actualArrival, scheduledArrival, "прилёта");
        if (actualDeparture != null && actualArrival.isBefore(actualDeparture)) {
            throw new BadRequestException(
                    "Фактическое время прилёта не может быть раньше фактического времени вылета");
        }
    }

    /** Валидация итоговых фактических времён после merge с текущими значениями рейса. */
    public void assertCorrection(
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            LocalDateTime now) {
        assertActualDeparture(actualDeparture, scheduledDeparture, now);
        assertActualArrival(actualArrival, actualDeparture, scheduledArrival, now);
    }

    /**
     * Для рейсов в будущем относительно MSK «сейчас» — baseline сдвигается к плановому якорю,
     * чтобы диспетчер мог вводить факт в день операции.
     */
    static LocalDateTime futureReference(LocalDateTime now, LocalDateTime scheduledAnchor) {
        if (now == null) {
            return null;
        }
        if (scheduledAnchor == null) {
            return now;
        }
        LocalDateTime anchor = scheduledAnchor.minusMinutes(SCHEDULED_FUTURE_LEAD_MINUTES);
        return anchor.isAfter(now) ? anchor : now;
    }

    private void assertSoftBounds(LocalDateTime actual, LocalDateTime scheduled, String label) {
        if (actual == null || scheduled == null) {
            return;
        }
        LocalDateTime earliest = scheduled.minusHours(24);
        LocalDateTime latest = scheduled.plusHours(48);
        if (actual.isBefore(earliest)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть более чем на 24 ч раньше планового");
        }
        if (actual.isAfter(latest)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть более чем на 48 ч позже планового");
        }
    }

    private void assertNotTooFarInFuture(LocalDateTime value, LocalDateTime reference, String label) {
        if (reference == null) {
            return;
        }
        LocalDateTime maxAllowed = reference.plusMinutes(maxFutureSkewMinutes);
        if (value.isAfter(maxAllowed)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть более чем на "
                            + maxFutureSkewMinutes + " мин в будущем относительно планового времени рейса");
        }
    }
}
