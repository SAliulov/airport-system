package ru.airport.websocket.payload;

import ru.airport.dto.DelayWarningRs;

/**
 * Сообщение в {@code /topic/delays}: идентификатор рейса, тело предупреждения и тип события
 * ({@code CREATED}/{@code UPDATED}/{@code DELETED}) — нужен клиенту, чтобы отличить отмену
 * предупреждения от создания/редактирования (форма payload иначе идентична).
 */
public record DelayWarningPush(Integer flightId, DelayWarningRs warning, String eventType) {
}
