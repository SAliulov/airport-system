package ru.airport.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.SizeCategory;

/**
 * Тело запроса: создание или обновление типа ВС.
 * Задача 1; REST: {@code POST /api/v1/aircraft-types}, {@code PUT /api/v1/aircraft-types/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AircraftTypeRq {

    @NotBlank
    @Size(min = 2, max = 4)
    @Pattern(regexp = "[A-Za-z0-9]{2,4}", message = "ICAO-код типа ВС: 2–4 латинских буквы или цифры")
    private String icaoCode;

    @PositiveOrZero
    @Digits(integer=3, fraction=0, message = "Не более 3-х знаков")
    private Integer passengerCapacity;

    @NotNull
    private SizeCategory sizeCategory;
}
