package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ответ REST: одна запись назначения гейта с краткими данными гейта.
 * Задача 4; вложение в {@link FlightRs}; история — {@code GET /api/v1/flights/{id}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateAssignmentRs {

    private Integer assignmentId;
    private LocalDateTime assignedFrom;
    private LocalDateTime assignedTo;
    private GateSummaryRs gate;
}
