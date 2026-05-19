package ru.airport.business;

import ru.airport.exception.ConflictException;
import ru.airport.model.SizeCategory;

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

    public void assertMayDelete(boolean inUse) {
        if (inUse) {
            throw new ConflictException("Нельзя удалить тип ВС: есть рейсы, использующие этот тип.");
        }
    }

    public void assertSizeCategoryUnchangedIfInUse(SizeCategory current, SizeCategory next, boolean inUse) {
        if (inUse && current != next) {
            throw new ConflictException(
                    "Нельзя сменить категорию размера у типа ВС, который уже используется рейсами.");
        }
    }
}
