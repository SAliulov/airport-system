package ru.airport.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * JVM default timezone = таймзона аэропорта ({@code airport.timezone}).
 * Все сравнения с БД выполняются через {@link AirportClock}.
 */
@Configuration
public class TimeZoneConfiguration {

    private final AirportProperties airportProperties;

    public TimeZoneConfiguration(AirportProperties airportProperties) {
        this.airportProperties = airportProperties;
    }

    @PostConstruct
    void applyAirportTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(airportProperties.getTimezone()));
    }
}
