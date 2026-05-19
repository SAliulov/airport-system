package ru.airport.business;

import ru.airport.exception.ConflictException;

public class ScheduleBusinessRules {

    public void assertMayDelete(boolean hasFlights) {
        if (hasFlights) {
            throw new ConflictException("Нельзя удалить расписание: есть связанные рейсы.");
        }
    }
}
