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

    public static final String AUTO_DELAY_REASON =
            "[AUTO] Превышено плановое время ожидания вылета (диспетчер не перевёл рейс в DEPARTED)";
    public static final String AUTO_INBOUND_GATE_DELAY_REASON =
            "[AUTO] Не назначен гейт к плановому прилёту в базовый аэропорт";
    public static final String AUTO_CANCEL_REASON =
            "[AUTO] Рейс не выполнен к плановому вылету";

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
