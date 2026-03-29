package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ответ REST: один сегмент занятости гейта для таймлайна.
 * Задача 7; {@code GET /api/v1/gates/timeline?date=}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateTimelineSegmentRs {

    private Integer gateId;
    private String gateNumber;
    private Integer assignmentId;
    private Integer flightId;
    private String flightNumber;
    private LocalDateTime assignedFrom;
    private LocalDateTime assignedTo;
}
