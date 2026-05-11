package ru.airport.validation;

import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.GateRq;
import ru.airport.dto.ScheduleRq;

/**
 * Нормализация строковых кодов перед сохранением (единый регистр в БД).
 */
public final class TextNormalization {

    private TextNormalization() {
    }

    public static String trimToUpperAscii(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.trim().toUpperCase();
    }

    /** IATA аэропорта: 3 буквы в верхнем регистре. */
    public static void normalizeScheduleAirports(ScheduleRq rq) {
        if (rq == null) {
            return;
        }
        rq.setOriginAirport(trimToUpperAscii(rq.getOriginAirport()));
        rq.setDestinationAirport(trimToUpperAscii(rq.getDestinationAirport()));
    }

    /** IATA авиакомпании: 2 символа в верхнем регистре. */
    public static void normalizeAirlineCodes(AirlineRq rq) {
        if (rq == null) {
            return;
        }
        rq.setIataCode(trimToUpperAscii(rq.getIataCode()));
    }

    /** ICAO типа ВС: в верхнем регистре. */
    public static void normalizeAircraftTypeCodes(AircraftTypeRq rq) {
        if (rq == null) {
            return;
        }
        rq.setIcaoCode(trimToUpperAscii(rq.getIcaoCode()));
    }

    /** Номер гейта: trim + верхний регистр латиницы/цифр. */
    public static void normalizeGateRequest(GateRq rq) {
        if (rq == null) {
            return;
        }
        rq.setGateNumber(trimToUpperAscii(rq.getGateNumber()));
    }
}
