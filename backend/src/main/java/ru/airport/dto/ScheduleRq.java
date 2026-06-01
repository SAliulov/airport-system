package ru.airport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.PeriodicityType;
import ru.airport.validation.DifferentAirports;
import ru.airport.validation.ValidScheduleSlots;

import java.time.LocalDate;
import java.util.List;

@ValidScheduleSlots
@DifferentAirports
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
    @Pattern(regexp = "[A-Za-z]{3}", message = "IATA аэропорта: 3 латинские буквы")
    private String originAirport;

    @NotBlank
    @Size(min = 3, max = 3)
    @Pattern(regexp = "[A-Za-z]{3}", message = "IATA аэропорта: 3 латинские буквы")
    private String destinationAirport;

    @NotNull
    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @Builder.Default
    private Boolean isActive = true;

    @NotNull
    private PeriodicityType periodicityType;

    @NotNull
    @Positive
    @Builder.Default
    private Integer periodicityStep = 1;

    @NotNull
    @Positive
    private Integer airlineId;

    @NotEmpty
    @Valid
    private List<ScheduleSlotRq> slots;
}
