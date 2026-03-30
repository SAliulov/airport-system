package ru.airport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.SizeCategory;

/**
 * Тело запроса: создание или обновление гейта (справочник).
 * Задача 4 (часть — справочник гейтов); REST: {@code POST /api/v1/gates}, {@code PUT /api/v1/gates/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateRq {

    @NotBlank
    @Size(max = 10)
    private String gateNumber;

    @Size(max = 10)
    private String terminal;

    @NotNull
    private Boolean isActive;

    @NotNull
    @Schema(description = "NARROW, WIDE или JUMBO", example = "WIDE")
    private SizeCategory maxSizeCategory;
}
