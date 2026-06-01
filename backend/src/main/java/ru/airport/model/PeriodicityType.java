package ru.airport.model;

/**
 * Тип повторения шаблона расписания.
 */
public enum PeriodicityType {
    /** День недели + шаг в неделях от {@code effective_from}. */
    WEEKLY,
    /** Каждые N календарных дней от {@code effective_from}. */
    INTERVAL
}
