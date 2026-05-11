package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тело запроса: создание конкретного выполняемого рейса по {@code scheduleId}.
 * Задача 2; REST: {@code POST /api/v1/flights}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightRq {

    @NotNull
    @Positive
    private Integer scheduleId;
}
