package ru.airport.websocket.payload;

import ru.airport.model.OperationalEventCategory;

/**
 * Сообщение в {@code /topic/operational-events}: человекочитаемое действие диспетчера.
 */
public record OperationalEventPush(
        String timestamp,
        String user,
        OperationalEventCategory category,
        String message,
        String details,
        Integer flightId,
        String flightNumber
) {
}
