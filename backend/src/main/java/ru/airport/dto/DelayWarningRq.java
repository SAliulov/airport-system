package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тело запроса: создать предупреждение о задержке рейса.
 * Задача 8; REST: {@code POST /api/v1/flights/{id}/delay-warnings}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DelayWarningRq {

    @NotNull
    @Positive
    private Integer delayMinutes;

    @Size(max = 500)
    private String reason;
}
