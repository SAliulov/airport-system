package ru.airport.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.websocket.payload.DelayWarningPush;
import ru.airport.websocket.payload.GateAssignmentPush;

/**
 * Рассылка событий клиентам (табло, мобилка) — вызывается из прикладных сервисов и планировщика.
 */
@Service
@RequiredArgsConstructor
public class RealtimeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void publishFlightUpdate(FlightRs flight) {
        messagingTemplate.convertAndSend("/topic/flights", flight);
    }

    public void publishDelayWarning(Integer flightId, DelayWarningRs warning) {
        messagingTemplate.convertAndSend("/topic/delays", new DelayWarningPush(flightId, warning));
    }

    public void publishGateChange(Integer flightId, GateAssignmentRs assignment) {
        messagingTemplate.convertAndSend("/topic/gate-changes", new GateAssignmentPush(flightId, assignment));
    }
}
