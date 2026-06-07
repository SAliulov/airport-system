package ru.airport.business;

import org.junit.jupiter.api.Test;
import ru.airport.exception.BadRequestException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightActualTimeRulesTest {

    private final FlightActualTimeRules rules = new FlightActualTimeRules(120);

    @Test
    void futureSkewUsesScheduledAnchorWhenFlightIsInFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime scheduledDeparture = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime actualDeparture = LocalDateTime.of(2026, 6, 16, 14, 5);

        assertThatCode(() -> rules.assertActualDeparture(actualDeparture, scheduledDeparture, now))
                .doesNotThrowAnyException();
    }

    @Test
    void futureSkewStillRejectsTooFarBeyondScheduledAnchor() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime scheduledDeparture = LocalDateTime.of(2026, 6, 16, 14, 0);
        LocalDateTime actualDeparture = scheduledDeparture.plusHours(3);

        assertThatThrownBy(() -> rules.assertActualDeparture(actualDeparture, scheduledDeparture, now))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("120 мин");
    }

    @Test
    void futureReferencePrefersScheduledLeadWhenLaterThanNow() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 19, 12, 0);
        LocalDateTime scheduled = LocalDateTime.of(2026, 6, 16, 14, 0);
        assertThat(FlightActualTimeRules.futureReference(now, scheduled))
                .isEqualTo(scheduled.minusMinutes(15));
    }
}
