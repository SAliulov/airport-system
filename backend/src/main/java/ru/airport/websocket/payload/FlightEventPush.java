package ru.airport.websocket.payload;

import java.util.Map;

/**
 * Обёртка для push-уведомления о рейсе: {@code eventType} = CREATED | UPDATED | DELETED.
 */
public record FlightEventPush(
        String eventType,
        Map<String, Object> flightData
) {
}
