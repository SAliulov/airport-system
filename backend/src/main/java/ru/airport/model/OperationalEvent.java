package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Персистентная запись операционного события (журнал действий диспетчера).
 * Заполняется из {@link ru.airport.websocket.payload.OperationalEventPush} рядом с публикацией
 * в /topic/operational-events — историю читает REST-эндпоинт (журнал на мобильном клиенте).
 */
@Entity
@Table(name = "operational_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OperationalEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operational_event_id")
    @EqualsAndHashCode.Include
    private Long operationalEventId;

    @Column(name = "event_timestamp", nullable = false)
    private LocalDateTime eventTimestamp;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20, nullable = false)
    private OperationalEventCategory category;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "flight_id")
    private Integer flightId;

    @Column(name = "flight_number", length = 20)
    private String flightNumber;
}
