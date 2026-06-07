package ru.airport.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.airport.service.FlightAutoStatusService;
import ru.airport.service.ScheduleExpiryService;

/**
 * Задача 3: автоматический переход статусов (время аэропорта — {@link ru.airport.config.AirportClock}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlightStatusScheduler {

    private final FlightAutoStatusService flightAutoStatusService;
    private final ScheduleExpiryService scheduleExpiryService;

    @Scheduled(fixedRateString = "${airport.scheduler.flight-status-ms:60000}")
    public void updateFlightStatuses() {
        int deactivated = scheduleExpiryService.deactivateExpiredSchedules();
        if (deactivated > 0) {
            log.info("Auto-deactivated {} expired schedule template(s)", deactivated);
        }
        flightAutoStatusService.runAutoStatusTick();
    }
}
