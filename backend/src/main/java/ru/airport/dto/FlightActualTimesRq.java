package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Тело запроса: коррекция фактических времён завершённого рейса.
 * REST: {@code PUT /api/v1/flights/{id}/actual-times}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightActualTimesRq {

    /** Новое фактическое время вылета; {@code null} — оставить текущее. */
    private LocalDateTime actualDeparture;

    /** Новое фактическое время прилёта; {@code null} — оставить текущее. */
    private LocalDateTime actualArrival;
}
