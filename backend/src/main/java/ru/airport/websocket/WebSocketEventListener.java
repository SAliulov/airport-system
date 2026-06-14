package ru.airport.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.airport.event.DelayWarningEvent;
import ru.airport.event.FlightCreatedEvent;
import ru.airport.event.FlightDeletedEvent;
import ru.airport.event.FlightUpdateEvent;
import ru.airport.event.GateChangeEvent;
import ru.airport.event.OperationalEvent;
import ru.airport.websocket.payload.DelayWarningPush;
import ru.airport.websocket.payload.GateAssignmentPush;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightUpdate(FlightUpdateEvent event) {
        Map<String, Object> payload = toFlightEventPayload("UPDATED", event.flightRs());
        messagingTemplate.convertAndSend("/topic/flights", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightCreated(FlightCreatedEvent event) {
        Map<String, Object> payload = toFlightEventPayload("CREATED", event.flightRs());
        messagingTemplate.convertAndSend("/topic/flights", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightDeleted(FlightDeletedEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("_eventType", "DELETED");
        payload.put("flightId", event.flightId());
        messagingTemplate.convertAndSend("/topic/flights", payload);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGateChange(GateChangeEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/gate-changes",
                new GateAssignmentPush(event.flightId(), event.assignmentRs())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDelayWarning(DelayWarningEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/delays",
                new DelayWarningPush(event.flightId(), event.warningRs())
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationalEvent(OperationalEvent event) {
        messagingTemplate.convertAndSend("/topic/operational-events", event.payload());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toFlightEventPayload(String eventType, Object flightRs) {
        Map<String, Object> map = objectMapper.convertValue(flightRs, Map.class);
        map.put("_eventType", eventType);
        return map;
    }
}