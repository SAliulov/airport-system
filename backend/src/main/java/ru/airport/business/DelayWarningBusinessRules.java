package ru.airport.business;

import ru.airport.exception.BadRequestException;
import ru.airport.model.FlightStatus;

/**
 * Ручное предупреждение о задержке — только для рейса в статусе DELAYED
 * (обычно сразу после ручного перевода SCHEDULED → DELAYED).
 */
public class DelayWarningBusinessRules {

    public void assertMayAddManualDelayWarning(FlightStatus status) {
        if (status != FlightStatus.DELAYED) {
            throw new BadRequestException(
                    "Предупреждение о задержке можно добавить только для рейса в статусе DELAYED");
        }
    }
}
