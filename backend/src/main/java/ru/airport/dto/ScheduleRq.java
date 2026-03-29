package ru.airport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Тело запроса: создание или обновление планового расписания (шаблон рейса).
 * Задача 2; REST: {@code POST /api/v1/schedules}, {@code PUT /api/v1/schedules/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRq {

    @NotBlank
    @Size(max = 20)
    private String flightNumber;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Z]{3}", message = "IATA аэропорта: 3 заглавные латинские буквы")
    private String originAirport;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Z]{3}", message = "IATA аэропорта: 3 заглавные латинские буквы")
    private String destinationAirport;

    @NotNull
    private LocalDateTime scheduledDeparture;

    @NotNull
    private LocalDateTime scheduledArrival;

    @NotNull
    private Integer airlineId;
}
