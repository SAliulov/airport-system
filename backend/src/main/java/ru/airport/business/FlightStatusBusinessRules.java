package ru.airport.business;

import ru.airport.exception.ConflictException;
import ru.airport.model.FlightStatus;

/**
 * Допустимые ручные и автоматические переходы статуса рейса.
 */
public class FlightStatusBusinessRules {

    public void assertManualTransition(FlightStatus from, FlightStatus to, FlightHomeAirportRules.OperationKind kind) {
        if (from == to) {
            return;
        }
        boolean allowed = switch (from) {
            case SCHEDULED -> to == FlightStatus.DEPARTED
                    || to == FlightStatus.DELAYED
                    || to == FlightStatus.CANCELLED;
            case DELAYED -> to == FlightStatus.DEPARTED
                    || to == FlightStatus.CANCELLED
                    || (kind == FlightHomeAirportRules.OperationKind.ARRIVAL && to == FlightStatus.ARRIVED);
            case DEPARTED -> to == FlightStatus.ARRIVED;
            case ARRIVED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ConflictException("Недопустимая смена статуса рейса: " + from + " → " + to);
        }
    }

    public void assertAutoDeparture(FlightStatus from) {
        if (from != FlightStatus.SCHEDULED && from != FlightStatus.DELAYED) {
            throw new ConflictException("Автовылет недопустим при статусе " + from);
        }
    }

    public void assertAutoArrival(FlightStatus from) {
        if (from != FlightStatus.DEPARTED) {
            throw new ConflictException("Автоприлёт недопустим при статусе " + from);
        }
    }

    public void assertAutoDelay(FlightStatus from) {
        if (from != FlightStatus.SCHEDULED && from != FlightStatus.DEPARTED) {
            throw new ConflictException("Автозадержка недопустима при статусе " + from);
        }
    }

    public void assertAutoDelayNoGate(FlightStatus from) {
        if (from != FlightStatus.DEPARTED) {
            throw new ConflictException("Автозадержка (нет гейта) недопустима при статусе " + from);
        }
    }

    public void assertAutoCancel(FlightStatus from) {
        if (from != FlightStatus.SCHEDULED && from != FlightStatus.DELAYED) {
            throw new ConflictException("Автоотмена недопустима при статусе " + from);
        }
    }
}
