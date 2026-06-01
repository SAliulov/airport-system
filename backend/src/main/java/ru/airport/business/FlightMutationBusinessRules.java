package ru.airport.business;

import ru.airport.exception.BadRequestException;
import ru.airport.exception.ConflictException;
import ru.airport.model.FlightStatus;

import java.time.LocalDateTime;

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

    /**
     * Один экземпляр рейса на слот и дату операции.
     */
    public void assertNoExistingFlightForSlot(boolean flightExists) {
        if (flightExists) {
            throw new ConflictException("Рейс на эту дату по выбранному слоту уже существует");
        }
    }

    /**
     * @deprecated используйте {@link #assertNoExistingFlightForSlot(boolean)}
     */
    @Deprecated
    public void assertNoExistingFlightForSchedule(boolean flightExists) {
        assertNoExistingFlightForSlot(flightExists);
    }

    /**
     * Коррекция фактических времён — только для завершённого рейса (ARRIVED).
     */
    public void assertActualTimesCorrectable(FlightStatus status) {
        if (status != FlightStatus.ARRIVED) {
            throw new BadRequestException(
                    "Коррекция фактических времён доступна только для рейсов в статусе ARRIVED");
        }
    }

    /**
     * В запросе коррекции должно быть хотя бы одно поле.
     */
    public void assertActualTimesCorrectionRequested(
            LocalDateTime requestedDeparture,
            LocalDateTime requestedArrival) {
        if (requestedDeparture == null && requestedArrival == null) {
            throw new BadRequestException("Укажите хотя бы одно фактическое время для коррекции");
        }
    }

    /**
     * Слот и дата операции у существующего рейса не меняются.
     */
    public void assertSlotNotChanged(Integer currentSlotId, Integer requestedSlotId) {
        if (requestedSlotId != null
                && currentSlotId != null
                && !currentSlotId.equals(requestedSlotId)) {
            throw new BadRequestException("Изменение слота у существующего рейса запрещено");
        }
    }

    public void assertOperationDateNotChanged(
            java.time.LocalDate currentDate,
            java.time.LocalDate requestedDate) {
        if (requestedDate != null
                && currentDate != null
                && !currentDate.equals(requestedDate)) {
            throw new BadRequestException("Изменение даты операции у существующего рейса запрещено");
        }
    }
}
