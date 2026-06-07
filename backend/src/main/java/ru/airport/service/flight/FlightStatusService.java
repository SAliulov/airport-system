package ru.airport.service.flight;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightActualTimeRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.business.FlightStatusBusinessRules;
import ru.airport.business.GateAssignmentBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.dto.FlightActualTimesRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.repository.FlightRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDateTime;

import static ru.airport.service.flight.FlightQuerySupport.touchCollections;

/**
 * Ручные переходы статуса и коррекция фактического времени.
 */
@Service
@RequiredArgsConstructor
public class FlightStatusService {

    private final FlightRepository flightRepository;
    private final DtoMapper mapper;
    private final FlightMutationBusinessRules flightMutationBusinessRules;
    private final FlightStatusBusinessRules flightStatusBusinessRules;
    private final FlightActualTimeRules flightActualTimeRules;
    private final GateAssignmentBusinessRules gateAssignmentBusinessRules;
    private final FlightHomeAirportRules flightHomeAirportRules;
    private final RealtimeNotificationService realtimeNotificationService;
    private final AirportClock airportClock;
    private final AirportProperties airportProperties;
    private final FlightQueryService flightQueryService;

    private String homeIata() {
        return airportProperties.getHomeIata();
    }

    @Transactional
    public FlightRs updateStatus(Integer flightId, FlightStatusUpdateRq rq) {
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        touchCollections(flight);
        Schedule schedule = flight.getSchedule();
        FlightHomeAirportRules.OperationKind kind =
                flightHomeAirportRules.resolveOperationKind(schedule, homeIata());
        flightStatusBusinessRules.assertManualTransition(flight.getStatus(), rq.getStatus(), kind);

        LocalDateTime now = airportClock.now();

        if (rq.getStatus() == FlightStatus.DEPARTED) {
            LocalDateTime actualDeparture = rq.getActualDeparture() != null
                    ? rq.getActualDeparture()
                    : (flight.getActualDeparture() != null ? flight.getActualDeparture() : now);
            flightActualTimeRules.assertActualDeparture(
                    actualDeparture, flight.getScheduledDeparture(), now);
            if (kind == FlightHomeAirportRules.OperationKind.DEPARTURE) {
                flightHomeAirportRules.assertManualTransitionToDeparted(flight, homeIata(), actualDeparture);
                GateAssignment active = flight.getActiveGateAssignment();
                gateAssignmentBusinessRules.assertActualTimeWithinGateInterval(
                        actualDeparture, active, "вылета");
            } else {
                flightHomeAirportRules.assertManualInboundDeparture(flight, homeIata(), actualDeparture);
            }
            flight.setActualDeparture(actualDeparture);
        } else if (rq.getStatus() == FlightStatus.ARRIVED) {
            LocalDateTime actualArrival = rq.getActualArrival() != null
                    ? rq.getActualArrival()
                    : flight.getActualArrival();
            flightActualTimeRules.assertActualArrival(
                    actualArrival, flight.getActualDeparture(), flight.getScheduledArrival(), now);
            if (kind == FlightHomeAirportRules.OperationKind.ARRIVAL) {
                flightHomeAirportRules.assertManualTransitionToArrived(flight, homeIata(), actualArrival);
                GateAssignment active = flight.getActiveGateAssignment();
                gateAssignmentBusinessRules.assertActualTimeWithinGateInterval(
                        actualArrival, active, "прилёта");
            } else {
                flightHomeAirportRules.assertManualRemoteArrival(flight, homeIata(), actualArrival);
            }
            if (actualArrival != null) {
                flight.setActualArrival(actualArrival);
            }
        } else if (rq.getStatus() == FlightStatus.CANCELLED) {
            GateAssignment active = flight.getActiveGateAssignment();
            gateAssignmentBusinessRules.closeActiveAssignmentAt(active, now);
        }

        flight.setStatus(rq.getStatus());
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }

    @Transactional
    public FlightRs correctActualTimes(Integer flightId, FlightActualTimesRq rq) {
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertActualTimesCorrectable(flight.getStatus());
        flightMutationBusinessRules.assertActualTimesCorrectionRequested(
                rq.getActualDeparture(), rq.getActualArrival());

        LocalDateTime mergedDeparture = rq.getActualDeparture() != null
                ? rq.getActualDeparture()
                : flight.getActualDeparture();
        LocalDateTime mergedArrival = rq.getActualArrival() != null
                ? rq.getActualArrival()
                : flight.getActualArrival();

        LocalDateTime now = airportClock.now();
        flightActualTimeRules.assertCorrection(
                mergedDeparture, mergedArrival,
                flight.getScheduledDeparture(), flight.getScheduledArrival(),
                now);

        if (rq.getActualDeparture() != null) {
            flight.setActualDeparture(rq.getActualDeparture());
        }
        if (rq.getActualArrival() != null) {
            flight.setActualArrival(rq.getActualArrival());
        }

        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightUpdate(rs);
        return rs;
    }
}
