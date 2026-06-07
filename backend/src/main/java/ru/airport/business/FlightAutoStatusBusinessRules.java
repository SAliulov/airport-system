package ru.airport.business;

import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Чистые проверки eligibility для автоматических переходов статуса (scheduler).
 * Без JPA, без side effects — только «пора ли» и расчёт минут задержки.
 */
public class FlightAutoStatusBusinessRules {

    public boolean shouldAutoCancel(
            Flight flight,
            Instant now,
            int cancelHoursAfterScheduledDeparture,
            Instant scheduledDepartureInstant) {
        FlightStatus status = flight.getStatus();
        if (status != FlightStatus.SCHEDULED && status != FlightStatus.DELAYED) {
            return false;
        }
        if (flight.getActualDeparture() != null) {
            return false;
        }
        Instant cancelAfter = scheduledDepartureInstant.plus(Duration.ofHours(cancelHoursAfterScheduledDeparture));
        return !now.isBefore(cancelAfter);
    }

    public boolean shouldOutboundAutoDelay(
            Flight flight,
            Instant now,
            int graceMinutes,
            Instant scheduledDepartureInstant) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            return false;
        }
        Instant delayAfter = scheduledDepartureInstant.plus(Duration.ofMinutes(graceMinutes));
        return !now.isBefore(delayAfter);
    }

    public boolean shouldInboundAutoDeparture(
            Flight flight,
            Instant now,
            Instant scheduledDepartureInstant) {
        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            return false;
        }
        if (flight.getActualDeparture() != null) {
            return false;
        }
        if (now.isBefore(scheduledDepartureInstant)) {
            return false;
        }
        return flight.getAircraftType() != null;
    }

    public boolean shouldOutboundAutoArrival(
            Flight flight,
            Instant now,
            Instant expectedArrivalInstant) {
        if (flight.getStatus() != FlightStatus.DEPARTED || flight.getActualDeparture() == null) {
            return false;
        }
        return !now.isBefore(expectedArrivalInstant);
    }

    public LocalDateTime expectedOutboundArrival(Flight flight) {
        Duration flightDuration = Duration.between(flight.getScheduledDeparture(), flight.getScheduledArrival());
        return flight.getActualDeparture().plus(flightDuration);
    }

    public boolean shouldInboundAutoDelayMissedDeparture(
            Flight flight,
            Instant now,
            int graceMinutes,
            Instant scheduledDepartureInstant) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            return false;
        }
        if (flight.getActualDeparture() != null) {
            return false;
        }
        Instant delayAfter = scheduledDepartureInstant.plus(Duration.ofMinutes(graceMinutes));
        if (now.isBefore(delayAfter)) {
            return false;
        }
        return flight.getAircraftType() == null;
    }

    public boolean shouldInboundAutoDelayNoGate(
            Flight flight,
            Instant now,
            int graceMinutesAfterScheduledArrival,
            Instant scheduledArrivalInstant) {
        if (flight.getStatus() != FlightStatus.DEPARTED) {
            return false;
        }
        if (flight.getActiveGateAssignment() != null) {
            return false;
        }
        Instant delayAfter = scheduledArrivalInstant.plus(Duration.ofMinutes(graceMinutesAfterScheduledArrival));
        return !now.isBefore(delayAfter);
    }

    /** Минуты задержки от планового момента до «сейчас», минимум 1. */
    public int delayMinutesSince(LocalDateTime scheduled, LocalDateTime now) {
        long minutes = ChronoUnit.MINUTES.between(scheduled, now);
        return (int) Math.max(1, minutes);
    }
}
