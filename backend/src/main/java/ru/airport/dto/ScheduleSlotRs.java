package ru.airport.dto;

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
public class ScheduleSlotRs {

    private Integer slotId;
    private Integer dayOfWeek;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
}
