package ru.airport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightAutoStatusBusinessRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightStatusBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.DelayWarning;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Schedule;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Оркестрация авто-правил для одного рейса: load → business rules → save → WebSocket.
 * Чистые проверки времени/статуса — в {@link FlightAutoStatusBusinessRules}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightAutoStatusProcessor {

    private final FlightRepository flightRepository;
    private final DelayWarningRepository delayWarningRepository;
    private final DtoMapper dtoMapper;
    private final RealtimeNotificationService realtimeNotificationService;
    private final FlightStatusBusinessRules flightStatusBusinessRules;
    private final FlightAutoStatusBusinessRules flightAutoStatusBusinessRules;
    private final FlightHomeAirportRules flightHomeAirportRules;
    private final AirportClock airportClock;
    private final AirportProperties airportProperties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFlightAutoRules(Integer flightId, Instant now) {
        Flight flight = loadFlightForProcessing(flightId);
        Schedule schedule = flight.getSchedule();
        String home = normalize(airportProperties.getHomeIata());
        String origin = normalize(schedule.getOriginAirport());
        String destination = normalize(schedule.getDestinationAirport());
        boolean fromHome = home.equals(origin);
        boolean toHome = home.equals(destination);

        tryAutoCancel(flight, now);

        flight = loadFlightForProcessing(flightId);
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            return;
        }

        if (fromHome && !toHome) {
            tryAutoDelay(flight, now);
            flight = loadFlightForProcessing(flightId);
            tryOutboundAutoArrival(flight, now);
            return;
        }

        if (toHome && !fromHome) {
            tryInboundAutoDeparture(flight, now);
            flight = loadFlightForProcessing(flightId);
            tryInboundAutoDelayMissedDeparture(flight, now);
            flight = loadFlightForProcessing(flightId);
            tryInboundAutoDelayNoGate(flight, now);
        }
    }

    private void tryAutoCancel(Flight flight, Instant now) {
        int cancelHours = airportProperties.getScheduler().getAutoCancelHoursAfterScheduledDeparture();
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldAutoCancel(flight, now, cancelHours, scheduledDep)) {
            return;
        }
        flightStatusBusinessRules.assertAutoCancel(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledDeparture(), FlightStatus.CANCELLED,
                FlightAutoStatusService.AUTO_CANCEL_REASON);
    }

    private void tryAutoDelay(Flight flight, Instant now) {
        int graceMinutes = airportProperties.getScheduler().getOutboundDelayGraceMinutes();
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoDelay(flight, now, graceMinutes, scheduledDep)) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledDeparture(), FlightStatus.DELAYED,
                FlightAutoStatusService.AUTO_DELAY_REASON);
    }

    private void tryInboundAutoDeparture(Flight flight, Instant now) {
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoDeparture(flight, now, scheduledDep)) {
            if (flight.getStatus() == FlightStatus.SCHEDULED || flight.getStatus() == FlightStatus.DELAYED) {
                if (flight.getActualDeparture() == null && !now.isBefore(scheduledDep)
                        && flight.getAircraftType() == null) {
                    log.info("Auto inbound departure skipped for flight {}: aircraft type not assigned",
                            flight.getFlightId());
                }
            }
            return;
        }
        flightStatusBusinessRules.assertAutoDeparture(flight.getStatus());
        LocalDateTime actualDeparture = flight.getScheduledDeparture();
        flightHomeAirportRules.assertAutoTransitionToDeparted(flight, airportProperties.getHomeIata(), actualDeparture);
        flight.setActualDeparture(actualDeparture);
        flight.setStatus(FlightStatus.DEPARTED);
        publishFlight(saved(flight));
    }

    private void tryOutboundAutoArrival(Flight flight, Instant now) {
        LocalDateTime expectedArrival = flightAutoStatusBusinessRules.expectedOutboundArrival(flight);
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoArrival(
                flight, now, airportClock.toInstant(expectedArrival))) {
            return;
        }
        flightStatusBusinessRules.assertAutoArrival(flight.getStatus());
        flightHomeAirportRules.assertAutoTransitionToArrived(flight, airportProperties.getHomeIata(), expectedArrival);
        flight.setActualArrival(expectedArrival);
        flight.setStatus(FlightStatus.ARRIVED);
        publishFlight(saved(flight));
    }

    private void tryInboundAutoDelayMissedDeparture(Flight flight, Instant now) {
        int graceMinutes = airportProperties.getScheduler().getOutboundDelayGraceMinutes();
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoDelayMissedDeparture(
                flight, now, graceMinutes, scheduledDep)) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledDeparture(), FlightStatus.DELAYED,
                FlightAutoStatusService.AUTO_INBOUND_MISSED_DEPARTURE_REASON);
    }

    private void tryInboundAutoDelayNoGate(Flight flight, Instant now) {
        int graceMinutes = airportProperties.getScheduler().getInboundGateDelayMinutes();
        Instant scheduledArr = airportClock.toInstant(flight.getScheduledArrival());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoDelayNoGate(
                flight, now, graceMinutes, scheduledArr)) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelayNoGate(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledArrival(), FlightStatus.DELAYED,
                FlightAutoStatusService.AUTO_INBOUND_GATE_DELAY_REASON);
    }

    private void applyDelaySideEffect(
            Flight flight,
            Instant now,
            LocalDateTime referenceTime,
            FlightStatus newStatus,
            String reason) {
        LocalDateTime nowLocal = airportClock.toLocal(now);
        int delayMinutes = flightAutoStatusBusinessRules.delayMinutesSince(referenceTime, nowLocal);
        flight.setStatus(newStatus);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(delayMinutes)
                .reason(reason)
                .createdAt(nowLocal)
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        var warningRs = dtoMapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs);
    }

    private Flight saved(Flight flight) {
        return flightRepository.save(flight);
    }

    private void publishFlight(Flight saved) {
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
    }

    private Flight loadFlightForProcessing(Integer flightId) {
        return flightRepository.findByIdForAutoProcessing(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
