package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ответ REST: плановое расписание с авиакомпанией.
 * Задача 2; {@code GET /api/v1/schedules}; вложение в {@link FlightRs}.
 */
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
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private AirlineRs airline;
}
