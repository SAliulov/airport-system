package ru.airport.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.airport.config.AirportProperties;
import ru.airport.exception.BadRequestException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightActualTimeRulesTest {

    private final AirportProperties props = new AirportProperties();
    private FlightActualTimeRules rules;

    @BeforeEach
    void setUp() {
        props.getScheduler().setMaxActualTimeFutureSkewMinutes(120);
        props.getScheduler().setMaxDepartureEarlyHours(24);
        props.getScheduler().setMaxDepartureLateHours(48);
        rules = new FlightActualTimeRules(props);
    }

    @Test
    void futureSkewRejectsWhenFlightIsFarInFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime scheduledDeparture = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime actualDeparture = LocalDateTime.of(2026, 6, 16, 14, 5);

        assertThatThrownBy(() -> rules.assertActualDeparture(
                actualDeparture, scheduledDeparture,
                FlightHomeAirportRules.OperationKind.DEPARTURE, now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("120 мин");
    }

    @Test
    void futureSkewStillRejectsTooFarBeyondScheduledAnchor() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime scheduledDeparture = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime actualDeparture = scheduledDeparture.plusHours(3);

        assertThatThrownBy(() -> rules.assertActualDeparture(
                actualDeparture, scheduledDeparture,
                FlightHomeAirportRules.OperationKind.DEPARTURE, now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("120 мин");
    }
}