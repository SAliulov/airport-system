package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.FlightStatus;

import java.time.LocalDateTime;

/**
 * Тело запроса: ручная смена статуса рейса (в т.ч. задержка/отмена).
 * Задачи 2–3; REST: {@code PUT /api/v1/flights/{id}/status}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightStatusUpdateRq {

    @NotNull
    private FlightStatus status;

    /** При переходе в DEPARTED — фактическое время вылета (если ещё не задано на рейсе). */
    private LocalDateTime actualDeparture;

    /** При переходе в ARRIVED — фактическое время прилёта (если ещё не задано на рейсе). */
    private LocalDateTime actualArrival;
}
