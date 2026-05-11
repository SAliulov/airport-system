package ru.airport.business;

import org.springframework.stereotype.Component;
import ru.airport.exception.ConflictException;

@Component
public class AirlineBusinessRules {

    public void assertIataUniqueForCreate(boolean codeAlreadyTaken, String iataCode) {
        if (codeAlreadyTaken) {
            throw new ConflictException("IATA-код уже занят: " + iataCode);
        }
    }

    public void assertIataUniqueForUpdate(int airlineId, String iataCode, Integer otherAirlineIdWithSameCode) {
        if (otherAirlineIdWithSameCode != null && !otherAirlineIdWithSameCode.equals(airlineId)) {
            throw new ConflictException("IATA-код уже занят: " + iataCode);
        }
    }

    public void assertMayDelete(boolean hasSchedules) {
        if (hasSchedules) {
            throw new ConflictException("Нельзя удалить авиакомпанию: есть связанные расписания.");
        }
    }
}
