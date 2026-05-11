/**
 * Исключения предметной области и глобальный обработчик HTTP ({@code @ControllerAdvice}).
 * Сервисы и {@code business} бросают узкие типы ({@link BadRequestException}, {@link ResourceNotFoundException},
 * {@link ConflictException}, {@link ServerErrorException}); {@link GlobalExceptionHandler} переводит их в JSON
 * {@code {"error":"..."}}.
 */
package ru.airport.exception;
