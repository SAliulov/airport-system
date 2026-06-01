package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.FlightStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ответ REST: конкретный рейс с планом, типом ВС, текущим назначением гейта.
 * Задачи 2–5, 8; {@code GET /api/v1/flights}, {@code GET /api/v1/flights/{id}}.
 * Поля {@code gateAssignments} и {@code delayWarnings} заполняют при детальном запросе, при необходимости.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightRs {

    private Integer flightId;
    private FlightStatus status;
    private LocalDate operationDate;
    private LocalDateTime scheduledDeparture;
    private LocalDateTime scheduledArrival;
    private LocalDateTime actualDeparture;
    private LocalDateTime actualArrival;
    private ScheduleRs schedule;
    private AircraftTypeRs aircraftType;
    private GateAssignmentRs currentGateAssignment;
    private List<GateAssignmentRs> gateAssignments;
    private List<DelayWarningRs> delayWarnings;
}
