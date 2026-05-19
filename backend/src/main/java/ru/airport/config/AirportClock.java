package ru.airport.config;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Текущее время аэропорта: сравнения планировщика — через {@link Instant}, хранение в БД — наивный local.
 */
@Component
public class AirportClock {

    private final ZoneId zoneId;

    public AirportClock(AirportProperties properties) {
        this.zoneId = ZoneId.of(properties.getTimezone());
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public Instant nowInstant() {
        return Instant.now();
    }

    public LocalDateTime now() {
        return LocalDateTime.now(zoneId);
    }

    public Instant toInstant(LocalDateTime airportLocal) {
        if (airportLocal == null) {
            return null;
        }
        return airportLocal.atZone(zoneId).toInstant();
    }

    public LocalDateTime toLocal(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, zoneId);
    }
}
