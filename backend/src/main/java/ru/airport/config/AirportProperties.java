package ru.airport.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Настройки домашнего аэропорта и планировщика ({@code airport.*} в application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "airport")
public class AirportProperties {

    private String homeIata = "SVO";
    private String timezone = "Europe/Moscow";
    private List<String> allowedOrigins = new ArrayList<>();

    /** Окно ±N часов вокруг планового вылета/прилёта для интервала гейта. */
    private int gatePlanWindowHours = 12;

    /** Минуты после assigned_to, в которые допустим факт вылета/прилёта у гейта. */
    private int gatePostGraceMinutes = 15;
    private int scheduleGenerationHorizonDays = 365;

    private Scheduler scheduler = new Scheduler();

    @Getter
    @Setter
    public static class Scheduler {
        private long flightStatusMs = 60_000L;
        private int outboundDelayGraceMinutes = 5;
        private int inboundGateDelayMinutes = 5;
        private int autoCancelHoursAfterScheduledDeparture = 24;

        // Исключаем магические числа: настраиваемые мягкие границы для фактического времени
        private int maxDepartureEarlyHours = 24;
        private int maxDepartureLateHours = 48;
        private int maxArrivalEarlyHours = 24;
        private int maxArrivalLateHours = 48;
    }
}
