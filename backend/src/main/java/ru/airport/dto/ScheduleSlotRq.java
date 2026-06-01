package ru.airport.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleSlotRq {

    /** При update — сохраняет slot_id для связанных рейсов. */
    private Integer slotId;

    /** ISO 1=Пн … 7=Вс; NULL для INTERVAL. */
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @NotNull
    private LocalTime departureTime;

    @NotNull
    private LocalTime arrivalTime;
}
