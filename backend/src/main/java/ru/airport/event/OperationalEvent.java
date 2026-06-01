package ru.airport.event;

import ru.airport.websocket.payload.OperationalEventPush;

public record OperationalEvent(OperationalEventPush payload) {
}
