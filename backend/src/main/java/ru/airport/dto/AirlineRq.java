package ru.airport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Тело запроса: создание или обновление авиакомпании.
 * Задача 1 (AGENTS §6); REST: {@code POST /api/v1/airlines}, {@code PUT /api/v1/airlines/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AirlineRq {

    @NotBlank
    @Size(min = 2, max = 2)
    @Pattern(regexp = "[A-Za-z0-9]{2}", message = "IATA-код авиакомпании: ровно 2 латинские буквы или цифры")
    private String iataCode;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 77)
    private String country;
}
