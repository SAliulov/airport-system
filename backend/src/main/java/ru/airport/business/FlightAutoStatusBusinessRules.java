package ru.airport.business;

import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Чистые проверки для автоматических переходов статуса (scheduler).
 * Строгое разделение Outbound (вылет из home) и Inbound (прилёт в home).
 * Без JPA, без side effects — только «пора ли» и расчёт минут задержки.
 */
public class FlightAutoStatusBusinessRules {

    // ──────────────────────────────────────────────────────────
    // OUTBOUND (SVO → foreign)
    // ──────────────────────────────────────────────────────────

    /**
     * Outbound: "Диспетчер проспал" — SCHEDULED, actualDeparture не задан,
     * прошло grace-время после планового вылета → DELAYED.
     */
    public boolean shouldOutboundAutoDelay(
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
        return !now.isBefore(delayAfter);
    }

    /**
     * Outbound: диспетчер заполнил actualDeparture + тип ВС + гейт,
     * настало время actualDeparture → DEPARTED.
     */
    public boolean shouldOutboundAutoDeparture(
            Flight flight,
            Instant now,
            Instant actualDepartureInstant) {
        FlightStatus status = flight.getStatus();
        if (status != FlightStatus.SCHEDULED && status != FlightStatus.DELAYED) {
            return false;
        }
        if (flight.getActualDeparture() == null) {
            return false;
        }
        if (flight.getAircraftType() == null) {
            return false;
        }
        if (flight.getActiveGateAssignment() == null) {
            return false;
        }
        return !now.isBefore(actualDepartureInstant);
    }

    /**
     * Outbound: прибытие в foreign-аэропорт — auto actualArrival = scheduledArrival,
     * когда наступило плановое время прибытия.
     */
    public boolean shouldOutboundAutoArrival(
            Flight flight,
            Instant now,
            Instant scheduledArrivalInstant) {
        if (flight.getStatus() != FlightStatus.DEPARTED) {
            return false;
        }
        return !now.isBefore(scheduledArrivalInstant);
    }

    // ──────────────────────────────────────────────────────────
    // INBOUND (foreign → SVO)
    // ──────────────────────────────────────────────────────────

    /**
     * Inbound: вылет из foreign-аэропорта — назначен тип ВС, наступило
     * плановое время вылета → auto DEPARTED (actualDeparture = scheduledDeparture).
     */
    public boolean shouldInboundAutoDeparture(
            Flight flight,
            Instant now,
            Instant scheduledDepartureInstant) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            return false;
        }
        if (flight.getActualDeparture() != null) {
            return false;
        }
        if (flight.getAircraftType() == null) {
            return false;
        }
        if (now.isBefore(scheduledDepartureInstant)) {
            return false;
        }
        return true;
    }

    /**
     * Inbound: прибытие в SVO — диспетчер не ввёл actualArrival и не назначил гейт
     * спустя grace-время после планового прибытия → DELAYED.
     */
    public boolean shouldInboundAutoDelay(
            Flight flight,
            Instant now,
            int graceMinutes,
            Instant scheduledArrivalInstant) {
        if (flight.getStatus() != FlightStatus.DEPARTED) {
            return false;
        }
        if (flight.getActualArrival() != null) {
            return false;
        }
        Instant delayAfter = scheduledArrivalInstant.plus(Duration.ofMinutes(graceMinutes));
        return !now.isBefore(delayAfter);
    }

    /**
     * Inbound: рейс в DELAYED более cancelHours (24ч) —→ CANCELLED.
     */
    public boolean shouldInboundAutoCancel(
            Flight flight,
            Instant now,
            int cancelHours,
            Instant scheduledArrivalInstant) {
        if (flight.getStatus() != FlightStatus.DELAYED) {
            return false;
        }
        Instant cancelAfter = scheduledArrivalInstant.plus(Duration.ofHours(cancelHours));
        return !now.isBefore(cancelAfter);
    }

    // ──────────────────────────────────────────────────────────
    // Outbound: SCHEDULED / DELAYED 24ч без изменений → CANCELLED
    // ──────────────────────────────────────────────────────────

    public boolean shouldOutboundAutoCancel(
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

    // ──────────────────────────────────────────────────────────
    // Утилиты
    // ──────────────────────────────────────────────────────────

    /** Минуты задержки от планового момента до «сейчас», минимум 1. */
    public int delayMinutesSince(LocalDateTime scheduled, LocalDateTime now) {
        long minutes = ChronoUnit.MINUTES.between(scheduled, now);
        return (int) Math.max(1, minutes);
    }

    /**
     * Ожидаемое время прибытия outbound-рейса:
     * actualDeparture + (scheduledArrival - scheduledDeparture).
     */
    public LocalDateTime expectedOutboundArrival(Flight flight) {
        Duration flightDuration = Duration.between(flight.getScheduledDeparture(), flight.getScheduledArrival());
        return flight.getActualDeparture().plus(flightDuration);
    }
}