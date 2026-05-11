package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Тело запроса: новое назначение гейта на рейс (интервал занятости).
 * Задача 4; REST: {@code POST /api/v1/flights/{id}/gate-assignment}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateAssignmentRq {

    @NotNull
    @Positive
    private Integer gateId;

    @NotNull
    private LocalDateTime assignedFrom;

    @NotNull
    private LocalDateTime assignedTo;
}
