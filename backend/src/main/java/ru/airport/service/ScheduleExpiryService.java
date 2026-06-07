package ru.airport.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.config.AirportClock;
import ru.airport.model.Schedule;
import ru.airport.repository.ScheduleRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Автоматическая деактивация шаблонов после окончания сезона ({@code effectiveTo}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleExpiryService {

    private final ScheduleRepository scheduleRepository;
    private final AirportClock airportClock;

    @Transactional
    public int deactivateExpiredSchedules() {
        LocalDate today = airportClock.now().toLocalDate();
        List<Schedule> expired = scheduleRepository.findActiveExpiredBefore(today);
        if (expired.isEmpty()) {
            return 0;
        }
        for (Schedule schedule : expired) {
            schedule.setIsActive(false);
            log.info("Schedule {} ({}) auto-deactivated: effectiveTo {} before {}",
                    schedule.getScheduleId(), schedule.getFlightNumber(), schedule.getEffectiveTo(), today);
        }
        scheduleRepository.saveAll(expired);
        return expired.size();
    }
}
