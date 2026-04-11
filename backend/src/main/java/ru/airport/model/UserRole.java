package ru.airport.model;

/**
 * Роль учётной записи (JWT / Spring Security). В БД — строка в {@code app_user.role}.
 */
public enum UserRole {
    /** Диспетчер: полный доступ на чтение и запись. */
    DISPATCHER,
    /** Оперативный персонал (мобилка): только чтение + /auth/me и /auth/logout. */
    READ_ONLY
}