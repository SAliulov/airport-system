package ru.airport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Обработка автоправил для одного рейса в отдельной транзакции (вызов только через Spring-прокси).
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
    private final AirportClock airportClock;
    private final AirportProperties airportProperties;

    private String homeIata() {
        return airportProperties.getHomeIata();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processFlightAutoRules(Integer flightId, Instant now) {
        Flight flight = loadFlightForProcessing(flightId);
        Schedule schedule = flight.getSchedule();
        String home = normalize(homeIata());
        String origin = normalize(schedule.getOriginAirport());
        String destination = normalize(schedule.getDestinationAirport());
        boolean fromHome = home.equals(origin);
        boolean toHome = home.equals(destination);

        tryAutoCancel(flight, schedule, now);

        flight = loadFlightForProcessing(flightId);
        if (flight.getStatus() == FlightStatus.CANCELLED) {
            return;
        }

        if (fromHome && !toHome) {
            tryAutoDelay(flight, schedule, now);
            flight = loadFlightForProcessing(flightId);
            tryOutboundAutoArrival(flight, schedule, now);
            return;
        }

        if (toHome && !fromHome) {
            tryInboundAutoDeparture(flight, schedule, now);
            flight = loadFlightForProcessing(flightId);
            tryInboundAutoDelayMissedDeparture(flight, schedule, now);
            flight = loadFlightForProcessing(flightId);
            tryInboundAutoDelayNoGate(flight, schedule, now);
        }
    }

    private void tryAutoCancel(Flight flight, Schedule schedule, Instant now) {
        FlightStatus status = flight.getStatus();
        if (status != FlightStatus.SCHEDULED && status != FlightStatus.DELAYED) {
            return;
        }
        if (flight.getActualDeparture() != null) {
            return;
        }
        int cancelHours = airportProperties.getScheduler().getAutoCancelHoursAfterScheduledDeparture();
        Instant cancelAfter = airportClock.toInstant(flight.getScheduledDeparture())
                .plus(Duration.ofHours(cancelHours));
        if (now.isBefore(cancelAfter)) {
            return;
        }
        flightStatusBusinessRules.assertAutoCancel(status);
        LocalDateTime nowLocal = airportClock.toLocal(now);
        long minutes = Duration.between(flight.getScheduledDeparture(), nowLocal).toMinutes();
        int delayMinutes = (int) Math.max(1, minutes);
        flight.setStatus(FlightStatus.CANCELLED);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(delayMinutes)
                .reason(FlightAutoStatusService.AUTO_CANCEL_REASON)
                .createdAt(nowLocal)
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        var warningRs = dtoMapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs);
    }

    private void tryAutoDelay(Flight flight, Schedule schedule, Instant now) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            return;
        }
        int graceMinutes = airportProperties.getScheduler().getOutboundDelayGraceMinutes();
        Instant delayAfter = airportClock.toInstant(flight.getScheduledDeparture())
                .plus(Duration.ofMinutes(graceMinutes));
        if (now.isBefore(delayAfter)) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        LocalDateTime nowLocal = airportClock.toLocal(now);
        long minutes = Duration.between(flight.getScheduledDeparture(), nowLocal).toMinutes();
        int delayMinutes = (int) Math.max(1, minutes);
        flight.setStatus(FlightStatus.DELAYED);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(delayMinutes)
                .reason(FlightAutoStatusService.AUTO_DELAY_REASON)
                .createdAt(nowLocal)
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        var warningRs = dtoMapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs);
    }

    private void tryInboundAutoDeparture(Flight flight, Schedule schedule, Instant now) {
        if (flight.getStatus() != FlightStatus.SCHEDULED && flight.getStatus() != FlightStatus.DELAYED) {
            return;
        }
        if (flight.getActualDeparture() != null) {
            return;
        }
        if (now.isBefore(airportClock.toInstant(flight.getScheduledDeparture()))) {
            return;
        }
        if (flight.getAircraftType() == null) {
            log.info("Auto inbound departure skipped for flight {}: aircraft type not assigned",
                    flight.getFlightId());
            return;
        }
        flightStatusBusinessRules.assertAutoDeparture(flight.getStatus());
        LocalDateTime actualDeparture = flight.getScheduledDeparture();
        FlightHomeAirportRules.assertAutoTransitionToDeparted(flight, homeIata(), actualDeparture);
        flight.setActualDeparture(actualDeparture);
        flight.setStatus(FlightStatus.DEPARTED);
        Flight saved = flightRepository.save(flight);
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
    }

    private void tryOutboundAutoArrival(Flight flight, Schedule schedule, Instant now) {
        if (flight.getStatus() != FlightStatus.DEPARTED || flight.getActualDeparture() == null) {
            return;
        }
        Duration flightDuration = Duration.between(flight.getScheduledDeparture(), flight.getScheduledArrival());
        LocalDateTime expectedArrival = flight.getActualDeparture().plus(flightDuration);
        if (now.isBefore(airportClock.toInstant(expectedArrival))) {
            return;
        }
        flightStatusBusinessRules.assertAutoArrival(flight.getStatus());
        FlightHomeAirportRules.assertAutoTransitionToArrived(flight, homeIata(), expectedArrival);
        flight.setActualArrival(expectedArrival);
        flight.setStatus(FlightStatus.ARRIVED);
        Flight saved = flightRepository.save(flight);
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
    }

    private void tryInboundAutoDelayMissedDeparture(Flight flight, Schedule schedule, Instant now) {
        if (flight.getStatus() != FlightStatus.SCHEDULED) {
            return;
        }
        if (flight.getActualDeparture() != null) {
            return;
        }
        int graceMinutes = airportProperties.getScheduler().getOutboundDelayGraceMinutes();
        Instant delayAfter = airportClock.toInstant(flight.getScheduledDeparture())
                .plus(Duration.ofMinutes(graceMinutes));
        if (now.isBefore(delayAfter)) {
            return;
        }
        if (flight.getAircraftType() != null) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelay(flight.getStatus());
        LocalDateTime nowLocal = airportClock.toLocal(now);
        long minutes = Duration.between(flight.getScheduledDeparture(), nowLocal).toMinutes();
        int delayMinutes = (int) Math.max(1, minutes);
        flight.setStatus(FlightStatus.DELAYED);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(delayMinutes)
                .reason(FlightAutoStatusService.AUTO_INBOUND_MISSED_DEPARTURE_REASON)
                .createdAt(nowLocal)
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        var warningRs = dtoMapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs);
    }

    private void tryInboundAutoDelayNoGate(Flight flight, Schedule schedule, Instant now) {
        if (flight.getStatus() != FlightStatus.DEPARTED) {
            return;
        }
        if (flight.getActiveGateAssignment() != null) {
            return;
        }
        int graceMinutes = airportProperties.getScheduler().getInboundGateDelayMinutes();
        Instant delayAfter = airportClock.toInstant(flight.getScheduledArrival())
                .plus(Duration.ofMinutes(graceMinutes));
        if (now.isBefore(delayAfter)) {
            return;
        }
        flightStatusBusinessRules.assertAutoDelayNoGate(flight.getStatus());
        LocalDateTime nowLocal = airportClock.toLocal(now);
        long minutes = Duration.between(flight.getScheduledArrival(), nowLocal).toMinutes();
        int delayMinutes = (int) Math.max(1, minutes);
        flight.setStatus(FlightStatus.DELAYED);
        DelayWarning warning = DelayWarning.builder()
                .delayMinutes(delayMinutes)
                .reason(FlightAutoStatusService.AUTO_INBOUND_GATE_DELAY_REASON)
                .createdAt(nowLocal)
                .flight(flight)
                .build();
        Flight saved = flightRepository.save(flight);
        var warningRs = dtoMapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
        realtimeNotificationService.publishDelayWarning(saved.getFlightId(), warningRs);
    }

    private Flight loadFlightForProcessing(Integer flightId) {
        return flightRepository.findByIdForAutoProcessing(flightId)
                .orElseThrow(() -> new ResourceNotFoundException("Flight", flightId));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
