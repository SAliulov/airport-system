package ru.airport.model;

/**
 * Статус конкретного выполняемого рейса (flight).
 * Автоматические переходы выполняет FlightStatusScheduler.
 * Ручная смена — только диспетчером через REST API.
 *
 * Допустимые переходы:
 *   SCHEDULED → DEPARTED  (авто: NOW >= scheduled_departure)
 *   DEPARTED  → ARRIVED   (авто: NOW >= scheduled_arrival)
 *   SCHEDULED → DELAYED   (вручную диспетчером)
 *   SCHEDULED → CANCELLED (вручную диспетчером)
 *   DELAYED   → DEPARTED  (авто или вручную)
 *   DELAYED   → CANCELLED (вручную)
 */
public enum FlightStatus {
    SCHEDULED,
    DEPARTED,
    ARRIVED,
    DELAYED,
    CANCELLED
}