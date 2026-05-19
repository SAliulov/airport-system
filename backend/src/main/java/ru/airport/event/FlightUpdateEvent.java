package ru.airport.event;

import ru.airport.dto.FlightRs;

public record FlightUpdateEvent(FlightRs flightRs) {
}
