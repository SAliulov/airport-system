package ru.airport.business;

import org.springframework.stereotype.Component;
import ru.airport.exception.ConflictException;
import ru.airport.model.FlightStatus;

/**
 * Допустимые ручные переходы статуса рейса (согласовано с описанием {@link FlightStatus}).
 */
@Component
public class FlightStatusBusinessRules {

    public void assertManualTransition(FlightStatus from, FlightStatus to) {
        if (from == to) {
            return;
        }
        boolean allowed = switch (from) {
            case SCHEDULED -> to == FlightStatus.DEPARTED
                    || to == FlightStatus.DELAYED
                    || to == FlightStatus.CANCELLED;
            case DELAYED -> to == FlightStatus.DEPARTED || to == FlightStatus.CANCELLED;
            case DEPARTED -> to == FlightStatus.ARRIVED;
            case ARRIVED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new ConflictException("Недопустимая смена статуса рейса: " + from + " → " + to);
        }
    }
}
