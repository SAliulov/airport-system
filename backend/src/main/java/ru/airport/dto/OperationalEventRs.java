package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.airport.model.OperationalEventCategory;

/**
 * Поля намеренно совпадают с {@link ru.airport.websocket.payload.OperationalEventPush},
 * чтобы мобильная модель OperationalEvent.fromJson парсила и REST-, и WS-ответ без изменений.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalEventRs {

    private Long operationalEventId;
    private String timestamp;
    private String user;
    private OperationalEventCategory category;
    private String message;
    private String details;
    private Integer flightId;
    private String flightNumber;
}
