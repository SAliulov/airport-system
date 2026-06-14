package ru.airport.event;

import ru.airport.dto.FlightRs;

public record FlightCreatedEvent(FlightRs flightRs) {
}