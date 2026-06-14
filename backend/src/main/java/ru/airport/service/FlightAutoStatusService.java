package ru.airport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.airport.config.AirportClock;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.repository.FlightRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Оркестрация тика планировщика (UC3): загрузка кандидатов и делегирование в {@link FlightAutoStatusProcessor}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlightAutoStatusService {

    public static final String AUTO_OUTBOUND_DELAY_REASON =
            "[Auto] Диспетчер проспал";
    public static final String AUTO_INBOUND_ARRIVAL_DELAY_REASON =
            "[Auto] Не введено фактическое время прибытия и не назначен гейт";
    public static final String AUTO_INBOUND_DEPARTURE_REASON =
            "[Auto] Автоматический вылет из аэропорта отправления (рейс в пути)";
    public static final String AUTO_INBOUND_CANCEL_REASON =
            "[Auto] Рейс задержан более 24 часов — автоматическая отмена";
    public static final String AUTO_OUTBOUND_CANCEL_REASON =
            "[Auto] Рейс не выполнен к плановому вылету";
    public static final String AUTO_OUTBOUND_ARRIVAL_REASON =
            "[Auto] Автоматическое прибытие по плановому времени";

    private final FlightRepository flightRepository;
    private final FlightAutoStatusProcessor flightAutoStatusProcessor;
    private final AirportClock airportClock;

    public void runAutoStatusTick() {
        Instant now = airportClock.nowInstant();
        List<Flight> candidates = flightRepository.findForAutoStatusProcessing(
                EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.DEPARTED));
        log.info("Flight auto-status tick: {} candidate(s), now={}", candidates.size(), now);
        int processed = 0;
        int skipped = 0;
        for (Flight candidate : candidates) {
            try {
                flightAutoStatusProcessor.processFlightAutoRules(candidate.getFlightId(), now);
                processed++;
            } catch (Exception ex) {
                skipped++;
                log.warn("Skip auto status for flight {}: {}", candidate.getFlightId(), ex.getMessage(), ex);
            }
        }
        log.info("Flight auto-status tick completed: processed={}, skipped={}", processed, skipped);
    }
}
