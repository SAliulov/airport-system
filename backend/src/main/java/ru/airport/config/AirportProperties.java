package ru.airport.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки домашнего аэропорта и планировщика ({@code airport.*} в application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "airport")
public class AirportProperties {

    private String homeIata = "SVO";
    private String timezone = "Europe/Moscow";
    private Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Scheduler {
        private long flightStatusMs = 60_000L;
        private int outboundDelayGraceMinutes = 5;
        private int inboundGateDelayMinutes = 5;
        private int autoCancelHoursAfterScheduledDeparture = 24;
        private int maxActualTimeFutureSkewMinutes = 120;
    }
}
