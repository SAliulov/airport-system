package ru.airport.business;

import org.springframework.stereotype.Component;
import ru.airport.exception.ConflictException;

@Component
public class AircraftTypeBusinessRules {

    public void assertIcaoUniqueForCreate(boolean codeAlreadyTaken, String icaoCode) {
        if (codeAlreadyTaken) {
            throw new ConflictException("Код ICAO уже занят: " + icaoCode);
        }
    }

    public void assertIcaoUniqueForUpdate(int aircraftTypeId, String icaoCode, Integer otherTypeIdWithSameCode) {
        if (otherTypeIdWithSameCode != null && !otherTypeIdWithSameCode.equals(aircraftTypeId)) {
            throw new ConflictException("Код ICAO уже занят: " + icaoCode);
        }
    }
}
