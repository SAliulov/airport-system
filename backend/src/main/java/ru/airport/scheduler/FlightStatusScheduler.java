package ru.airport.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.repository.FlightRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDateTime;

/**
 * Задача 3 (FirstLab): автоматический переход статусов по плановому времени.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FlightStatusScheduler {

    private final FlightRepository flightRepository;
    private final DtoMapper dtoMapper;
    private final RealtimeNotificationService realtimeNotificationService;

    @Scheduled(fixedRateString = "${airport.scheduler.flight-status-ms:60000}")
    @Transactional
    public void updateFlightStatuses() {
        LocalDateTime now = LocalDateTime.now();

        for (Flight f : flightRepository.findReadyToDeparture(now)) {
            f.setStatus(FlightStatus.DEPARTED);
            f.setActualDeparture(now);
            Flight saved = flightRepository.save(f);
            realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
            log.debug("Flight {} auto DEPARTED", saved.getFlightId());
        }

        for (Flight f : flightRepository.findReadyToArrive(now)) {
            f.setStatus(FlightStatus.ARRIVED);
            f.setActualArrival(now);
            Flight saved = flightRepository.save(f);
            realtimeNotificationService.publishFlightUpdate(dtoMapper.toFlightRsSummary(saved));
            log.debug("Flight {} auto ARRIVED", saved.getFlightId());
        }
    }
}
