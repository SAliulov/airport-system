package ru.airport.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.airport.service.FlightAutoStatusService;

/**
 * Задача 3: автоматический переход статусов (время аэропорта — {@link ru.airport.config.AirportClock}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlightStatusScheduler {

    private final FlightAutoStatusService flightAutoStatusService;

    @Scheduled(fixedRateString = "${airport.scheduler.flight-status-ms:60000}")
    public void updateFlightStatuses() {
        flightAutoStatusService.runAutoStatusTick();
    }
}
