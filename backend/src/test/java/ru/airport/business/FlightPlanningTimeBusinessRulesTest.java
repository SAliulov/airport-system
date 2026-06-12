package ru.airport.business;

import org.junit.jupiter.api.Test;
import ru.airport.exception.BadRequestException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlightPlanningTimeBusinessRulesTest {

    private final FlightPlanningTimeBusinessRules rules = new FlightPlanningTimeBusinessRules();

    @Test
    void scheduledDepartureMustBeInFuture() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 18, 59, 55);

        assertThatCode(() -> rules.assertScheduledDepartureInFuture(
                LocalDateTime.of(2026, 6, 7, 19, 5), now))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> rules.assertScheduledDepartureInFuture(
                LocalDateTime.of(2026, 6, 7, 13, 0), now))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void generationCannotStartBeforeAirportToday() {
        LocalDate today = LocalDate.of(2026, 6, 7);

        assertThatCode(() -> rules.assertGenerationStartsTodayOrLater(today, today))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> rules.assertGenerationStartsTodayOrLater(today.minusDays(1), today))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void futurePredicateRejectsCurrentMoment() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 7, 18, 59, 55);

        assertThat(rules.isScheduledDepartureInFuture(now, now)).isFalse();
        assertThat(rules.isScheduledDepartureInFuture(now.plusSeconds(1), now)).isTrue();
    }
}
