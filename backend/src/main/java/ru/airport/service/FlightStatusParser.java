package ru.airport.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.airport.model.FlightStatus;

/**
 * Разбор query-параметра {@code status} без привязки контроллеров к {@link FlightStatus}.
 */
public final class FlightStatusParser {

    private FlightStatusParser() {
    }

    /**
     * @param raw значение из query; {@code null} или пустая строка → {@code null}
     */
    public static FlightStatus parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FlightStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Некорректный параметр status: «" + raw + "»; ожидается одно из: SCHEDULED, DEPARTED, ARRIVED, DELAYED, CANCELLED");
        }
    }
}
