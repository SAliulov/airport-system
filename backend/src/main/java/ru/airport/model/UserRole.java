package ru.airport.model;

/**
 * Роль учётной записи (JWT / Spring Security). В БД — строка в {@code app_user.role}.
 */
public enum UserRole {
    DISPATCHER,
    READ_ONLY
}
