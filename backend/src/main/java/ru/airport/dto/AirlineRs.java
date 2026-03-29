package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ответ REST: авиакомпания.
 * Задача 1; эндпоинты: {@code GET /api/v1/airlines}, {@code GET /api/v1/airlines/{id}} (в составе других DTO).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineRs {

    private Integer airlineId;
    private String iataCode;
    private String name;
    private String country;
}
