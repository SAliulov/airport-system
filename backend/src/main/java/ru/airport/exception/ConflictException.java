package ru.airport.exception;

/**
 * Конфликт с текущим состоянием данных (занятость гейта, дубликат ключа, недопустимый переход).
 * Для последующего маппинга в HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
