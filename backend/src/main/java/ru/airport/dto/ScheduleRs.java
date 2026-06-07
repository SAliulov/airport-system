package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.PeriodicityType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleRs {

    private Integer scheduleId;
    private String flightNumber;
    private String originAirport;
    private String destinationAirport;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Boolean isActive;
    private PeriodicityType periodicityType;
    private Integer periodicityStep;
    private AirlineRs airline;
    private List<ScheduleSlotRs> slots;

    /** Вычисляемые поля при фильтрации по дате (optional). */
    private LocalDateTime departureAtDate;
    private LocalDateTime arrivalAtDate;

    /** UI-hint: период продлён, но шаблон выключен — можно включить вручную. */
    private Boolean reactivationSuggested;
}
