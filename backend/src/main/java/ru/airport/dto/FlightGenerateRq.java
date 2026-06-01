package ru.airport.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightGenerateRq {

    @NotNull
    private LocalDate fromDate;

    @NotNull
    private LocalDate toDate;

    /** Если null — все активные шаблоны. */
    @Positive
    private Integer scheduleId;
}
