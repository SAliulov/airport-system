package ru.airport.websocket.payload;

import ru.airport.dto.GateAssignmentRs;

/**
 * Сообщение в {@code /topic/gate-changes}.
 */
public record GateAssignmentPush(Integer flightId, GateAssignmentRs assignment) {
}
