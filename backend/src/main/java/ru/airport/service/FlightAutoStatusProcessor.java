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
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.Instant;
import java.time.LocalDateTime;

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

        FlightHomeAirportRules.OperationKind kind;
        try {
            kind = flightHomeAirportRules.resolveOperationKind(
                    flight.getSchedule(), airportProperties.getHomeIata());
        } catch (Exception ex) {
            log.warn("Flight {} skipped: invalid home route — {}", flightId, ex.getMessage());
            return;
        }

        if (kind == FlightHomeAirportRules.OperationKind.DEPARTURE) {
            processOutbound(flight, now);
        } else {
            processInbound(flight, now);
        }
    }

    // ───── Outbound (SVO → foreign) ─────────────────────────────────

    private void processOutbound(Flight flight, Instant now) {
        if (tryOutboundAutoDelay(flight, now)) return;
        if (tryOutboundAutoDeparture(flight, now)) return;
        tryOutboundAutoArrival(flight, now);
        Flight fresh = loadFlightForProcessing(flight.getFlightId());
        tryOutboundAutoCancel(fresh, now);
    }

    private boolean tryOutboundAutoDelay(Flight flight, Instant now) {
        int graceMinutes = airportProperties.getScheduler().getOutboundDelayGraceMinutes();
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoDelay(flight, now, graceMinutes, scheduledDep)) {
            return false;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledDeparture(), FlightStatus.DELAYED,
                FlightAutoStatusService.AUTO_OUTBOUND_DELAY_REASON);
        return true;
    }

    private boolean tryOutboundAutoDeparture(Flight flight, Instant now) {
        Instant actualDepartureInstant = airportClock.toInstant(flight.getActualDeparture());
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoDeparture(flight, now, actualDepartureInstant)) {
            return false;
        }
        flightStatusBusinessRules.assertAutoDeparture(flight.getStatus());
        flightHomeAirportRules.assertOutboundAutoDeparture(
                flight, airportProperties.getHomeIata(), flight.getActualDeparture());
        flight.setStatus(FlightStatus.DEPARTED);
        publishFlight(saved(flight));
        return true;
    }

    private void tryOutboundAutoArrival(Flight flight, Instant now) {
        Instant scheduledArrivalInstant = airportClock.toInstant(flight.getScheduledArrival());
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoArrival(flight, now, scheduledArrivalInstant)) {
            return;
        }
        flightStatusBusinessRules.assertAutoArrival(flight.getStatus());
        flightHomeAirportRules.assertAutoTransitionToArrived(
                flight, airportProperties.getHomeIata(), flight.getScheduledArrival());
        flight.setActualArrival(flight.getScheduledArrival());
        flight.setStatus(FlightStatus.ARRIVED);
        publishFlight(saved(flight));
    }

    private void tryOutboundAutoCancel(Flight flight, Instant now) {
        int cancelHours = airportProperties.getScheduler().getAutoCancelHoursAfterScheduledDeparture();
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldOutboundAutoCancel(flight, now, cancelHours, scheduledDep)) {
            return;
        }
        flightStatusBusinessRules.assertAutoCancel(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledDeparture(), FlightStatus.CANCELLED,
                FlightAutoStatusService.AUTO_OUTBOUND_CANCEL_REASON);
    }

    // ───── Inbound (foreign → SVO) ─────────────────────────────────

    private void processInbound(Flight flight, Instant now) {
        if (tryInboundAutoDeparture(flight, now)) return;
        Flight fresh1 = loadFlightForProcessing(flight.getFlightId());
        if (tryInboundAutoDelay(fresh1, now)) return;
        Flight fresh2 = loadFlightForProcessing(flight.getFlightId());
        tryInboundAutoCancel(fresh2, now);
    }

    private boolean tryInboundAutoDeparture(Flight flight, Instant now) {
        Instant scheduledDep = airportClock.toInstant(flight.getScheduledDeparture());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoDeparture(flight, now, scheduledDep)) {
            return false;
        }
        flightStatusBusinessRules.assertAutoDeparture(flight.getStatus());
        LocalDateTime actualDeparture = flight.getScheduledDeparture();
        flightHomeAirportRules.assertAutoTransitionToDeparted(
                flight, airportProperties.getHomeIata(), actualDeparture);
        flight.setActualDeparture(actualDeparture);
        flight.setStatus(FlightStatus.DEPARTED);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(0)
                .reason(FlightAutoStatusService.AUTO_INBOUND_DEPARTURE_REASON)
                .createdAt(airportClock.now())
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        delayWarningRepository.save(warning);
        publishFlight(saved);
        return true;
    }

    private boolean tryInboundAutoDelay(Flight flight, Instant now) {
        int graceMinutes = airportProperties.getScheduler().getInboundGateDelayMinutes();
        Instant scheduledArr = airportClock.toInstant(flight.getScheduledArrival());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoDelay(flight, now, graceMinutes, scheduledArr)) {
            return false;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledArrival(), FlightStatus.DELAYED,
                FlightAutoStatusService.AUTO_INBOUND_ARRIVAL_DELAY_REASON);
        return true;
    }

    private void tryInboundAutoCancel(Flight flight, Instant now) {
        int cancelHours = airportProperties.getScheduler().getAutoCancelHoursAfterScheduledDeparture();
        Instant scheduledArr = airportClock.toInstant(flight.getScheduledArrival());
        if (!flightAutoStatusBusinessRules.shouldInboundAutoCancel(flight, now, cancelHours, scheduledArr)) {
            return;
        }
        flightStatusBusinessRules.assertAutoCancel(flight.getStatus());
        applyDelaySideEffect(flight, now, flight.getScheduledArrival(), FlightStatus.CANCELLED,
                FlightAutoStatusService.AUTO_INBOUND_CANCEL_REASON);
    }

    // ───── Общие утилиты ──────────────────────────────────────────

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
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs, "CREATED");
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
}