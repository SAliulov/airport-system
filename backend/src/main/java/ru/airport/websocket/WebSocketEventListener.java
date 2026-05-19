package ru.airport.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.airport.event.DelayWarningEvent;
import ru.airport.event.FlightUpdateEvent;
import ru.airport.event.GateChangeEvent;
import ru.airport.websocket.payload.DelayWarningPush;
import ru.airport.websocket.payload.GateAssignmentPush;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightUpdate(FlightUpdateEvent event) {
        messagingTemplate.convertAndSend("/topic/flights", event.flightRs());
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
}
