package ru.airport.websocket.payload;

import ru.airport.dto.DelayWarningRs;

/**
 * Сообщение в {@code /topic/delays}: идентификатор рейса + тело предупреждения.
 */
public record DelayWarningPush(Integer flightId, DelayWarningRs warning) {
}
