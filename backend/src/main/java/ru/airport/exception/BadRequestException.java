package ru.airport.exception;

/**
 * Некорректный ввод или параметры запроса (HTTP 400).
 * Обрабатывается в {@link GlobalExceptionHandler}.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
