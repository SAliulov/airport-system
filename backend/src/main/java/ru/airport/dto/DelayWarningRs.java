package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ответ REST: предупреждение о задержке.
 * Задача 8; {@code GET /api/v1/flights/{id}/delay-warnings}; вложение в {@link FlightRs}.
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
}
