package ru.airport.event;

import ru.airport.dto.DelayWarningRs;

public record DelayWarningEvent(Integer flightId, DelayWarningRs warningRs, String eventType) {
}
