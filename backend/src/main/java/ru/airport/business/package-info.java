/**
 * Слой бизнес-логики (FirstLab §3.3, сквозная валидация §6.3): конфликты ресурсов, граф статусов,
 * ограничения изменения расписания при связанных рейсах. Без JPA и без DTO — только сущности и {@link ru.airport.dto} на входе правил.
 * Формат полей запроса — {@code ru.airport.validation} + {@code @Valid} на DTO; ответы — {@code ru.airport.exception}.
 */
package ru.airport.business;
