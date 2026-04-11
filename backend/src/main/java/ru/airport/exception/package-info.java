/**
 * Исключения предметной области и глобальный обработчик HTTP ({@code @ControllerAdvice}).
 * Сервисы бросают узкие типы; {@link ru.airport.exception.GlobalExceptionHandler} переводит их в статусы ответа.
 */
package ru.airport.exception;
