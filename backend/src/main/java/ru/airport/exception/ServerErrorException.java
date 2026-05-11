package ru.airport.exception;

/**
 * Внутренняя ошибка приложения (HTTP 500). Используется для нештатных состояний,
 * которые не должны возникать при нормальной работе.
 */
public class ServerErrorException extends RuntimeException {

    public ServerErrorException(String message) {
        super(message);
    }
}
