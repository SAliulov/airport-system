package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ответ REST: плановое расписание с авиакомпанией (шаблон маршрута и времён).
 * Задача 2; {@code GET /api/v1/schedules}; вложение в {@link FlightRs}.
 * <p>
 * Операционный статус рейса ({@code SCHEDULED}, {@code ARRIVED}, …) и назначенный гейт
 * живут у сущности {@code Flight}, а не здесь: одно расписание может иметь несколько
 * выполняемых рейсов или пока ни одного — тогда в API статус смотрите через {@code /flights}.
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
