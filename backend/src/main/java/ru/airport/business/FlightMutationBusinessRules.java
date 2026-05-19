package ru.airport.business;

import ru.airport.exception.BadRequestException;
import ru.airport.exception.ConflictException;
import ru.airport.model.FlightStatus;

/**
 * Инварианты мутаций рейса по статусу (отдельно от графа переходов статуса).
 */
public class FlightMutationBusinessRules {

    private static final String CLOSED_HISTORY_MESSAGE =
            "Нельзя изменять ресурсы или расписание для вылетевшего/прибывшего рейса";

    private static final String CANCELLED_MESSAGE =
            "Нельзя изменять ресурсы или расписание для отменённого рейса";

    private static final String ARRIVED_LOCKED_MESSAGE =
            "Рейс прибыл — редактирование запрещено";

    /**
     * Любые изменения рейса (в т.ч. статус, задержка) запрещены после прилёта.
     */
    public void assertFlightEditable(FlightStatus status) {
        if (status == FlightStatus.ARRIVED) {
            throw new BadRequestException(ARRIVED_LOCKED_MESSAGE);
        }
    }

    /**
     * Расписание, гейт и тип ВС — только пока рейс SCHEDULED или DELAYED.
     */
    public void assertResourcesMutable(FlightStatus status) {
        if (status == FlightStatus.DEPARTED || status == FlightStatus.ARRIVED) {
            throw new BadRequestException(CLOSED_HISTORY_MESSAGE);
        }
        if (status == FlightStatus.CANCELLED) {
            throw new BadRequestException(CANCELLED_MESSAGE);
        }
    }

    public void assertScheduleMutable(FlightStatus status) {
        assertResourcesMutable(status);
    }

    /**
     * Удаление разрешено только для SCHEDULED и CANCELLED.
     */
    public void assertFlightDeletable(FlightStatus status) {
        if (status != FlightStatus.SCHEDULED && status != FlightStatus.CANCELLED) {
            throw new ConflictException(
                    "Удаление разрешено только для рейсов в статусах SCHEDULED или CANCELLED");
        }
    }
}
