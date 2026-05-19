package ru.airport.event;

import ru.airport.dto.GateAssignmentRs;

public record GateChangeEvent(Integer flightId, GateAssignmentRs assignmentRs) {
}
