package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.FlightStatus;

import java.time.LocalDateTime;

/**
 * Ответ REST: предупреждение о задержке.
 * Задача 8; {@code GET /api/v1/flights/{id}/delay-warnings}; вложение в {@link FlightRs}.
 * Поля с контекстом рейса заполняются также для {@code GET /api/v1/delay-warnings}
 * (сводный список по всем рейсам для вкладки «Задержки») — при вложении в {@link FlightRs}
 * избыточны, но безвредны.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayWarningRs {

    private Integer warningId;
    private Integer delayMinutes;
    private String reason;
    private LocalDateTime createdAt;

    private Integer flightId;
    private String flightNumber;
    private String originAirport;
    private String destinationAirport;
    private LocalDateTime scheduledDeparture;
    private FlightStatus flightStatus;
}
