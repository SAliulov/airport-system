package ru.airport.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.event.DelayWarningEvent;
import ru.airport.event.FlightCreatedEvent;
import ru.airport.event.FlightDeletedEvent;
import ru.airport.event.FlightUpdateEvent;
import ru.airport.event.GateChangeEvent;
import ru.airport.event.OperationalEvent;
import ru.airport.service.OperationalEventService;
import ru.airport.websocket.payload.OperationalEventPush;

/**
 * Рассылка событий клиентам (табло, мобилка) — вызывается из прикладных сервисов и планировщика.
 */
@Service
@RequiredArgsConstructor
public class RealtimeNotificationService {

    private final ApplicationEventPublisher eventPublisher;
    private final OperationalEventService operationalEventService;

    public void publishFlightUpdate(FlightRs flight) {
        eventPublisher.publishEvent(new FlightUpdateEvent(flight));
    }

    public void publishFlightCreated(FlightRs flight) {
        eventPublisher.publishEvent(new FlightCreatedEvent(flight));
    }

    public void publishFlightDeleted(Integer flightId) {
        eventPublisher.publishEvent(new FlightDeletedEvent(flightId));
    }

    public void publishDelayWarning(Integer flightId, DelayWarningRs warning, String eventType) {
        eventPublisher.publishEvent(new DelayWarningEvent(flightId, warning, eventType));
    }

    public void publishGateChange(Integer flightId, GateAssignmentRs assignment) {
        eventPublisher.publishEvent(new GateChangeEvent(flightId, assignment));
    }

    public void publishOperationalEvent(OperationalEventPush event) {
        eventPublisher.publishEvent(new OperationalEvent(event));
        operationalEventService.persist(event);
    }
}
