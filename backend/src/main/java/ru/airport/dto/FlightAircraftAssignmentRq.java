package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тело запроса: назначить тип ВС на рейс.
 * Задача 5; REST: {@code PUT /api/v1/flights/{id}/aircraft}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightAircraftAssignmentRq {

    @NotNull
    @Positive
    private Integer aircraftTypeId;
}
