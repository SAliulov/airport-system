package ru.airport.business;

import org.springframework.stereotype.Component;
import ru.airport.exception.ConflictException;

@Component
public class GateBusinessRules {

    public void assertGateNumberUniqueForCreate(boolean numberTaken, String gateNumber) {
        if (numberTaken) {
            throw new ConflictException("Номер гейта уже занят: " + gateNumber);
        }
    }

    public void assertGateNumberUniqueForUpdate(int gateId, String gateNumber, Integer otherGateIdWithSameNumber) {
        if (otherGateIdWithSameNumber != null && !otherGateIdWithSameNumber.equals(gateId)) {
            throw new ConflictException("Номер гейта уже занят: " + gateNumber);
        }
    }

    public void assertMayDelete(boolean hasAssignments) {
        if (hasAssignments) {
            throw new ConflictException("Нельзя удалить гейт: есть связанные назначения.");
        }
    }
}
