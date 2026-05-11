package ru.airport.business;

import org.springframework.stereotype.Component;
import ru.airport.exception.ConflictException;

@Component
public class ScheduleBusinessRules {

    public void assertMayDelete(boolean hasFlights) {
        if (hasFlights) {
            throw new ConflictException("Нельзя удалить расписание: есть связанные рейсы.");
        }
    }
}
