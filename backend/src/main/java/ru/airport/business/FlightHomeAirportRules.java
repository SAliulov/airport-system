package ru.airport.business;

import ru.airport.exception.BadRequestException;
import ru.airport.model.Flight;
import ru.airport.model.Schedule;

import java.time.LocalDateTime;

/**
 * Правила домашнего аэропорта: направление рейса и инварианты перехода в DEPARTED/ARRIVED.
 */
public class FlightHomeAirportRules {

    public enum OperationKind {
        DEPARTURE,
        ARRIVAL
    }

    public OperationKind resolveOperationKind(Schedule schedule, String homeIata) {
        String home = normalize(homeIata);
        String origin = normalize(schedule.getOriginAirport());
        String destination = normalize(schedule.getDestinationAirport());
        boolean fromHome = home.equals(origin);
        boolean toHome = home.equals(destination);
        if (fromHome && !toHome) {
            return OperationKind.DEPARTURE;
        }
        if (toHome && !fromHome) {
            return OperationKind.ARRIVAL;
        }
        throw new BadRequestException("Рейс не относится к базовому аэропорту");
    }

    /** Маршрут должен проходить через базовый аэропорт ровно на одном конце (вылет или прилёт). */
    public void assertValidHomeRoute(Schedule schedule, String homeIata) {
        resolveOperationKind(schedule, homeIata);
    }

    public void assertManualTransitionToDeparted(Flight flight, String homeIata, LocalDateTime actualDeparture) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind != OperationKind.DEPARTURE) {
            throw new BadRequestException(
                    "Перевод в статус DEPARTED допустим только для рейсов на вылет из базового аэропорта");
        }
        assertHomeDepartureRequirements(flight, actualDeparture);
    }

    public void assertManualInboundDeparture(Flight flight, String homeIata, LocalDateTime actualDeparture) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind != OperationKind.ARRIVAL) {
            throw new BadRequestException(
                    "Ручной вылет из аэропорта отправления допустим только для рейсов на прилёт в базовый аэропорт");
        }
        assertActualDeparturePresent(actualDeparture);
        assertAircraftTypeAssigned(flight);
    }

    /** Прилёт в базовый аэропорт (origin → SVO). */
    public void assertManualTransitionToArrived(Flight flight, String homeIata, LocalDateTime actualArrival) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind != OperationKind.ARRIVAL) {
            throw new BadRequestException(
                    "Перевод в статус ARRIVED в SVO допустим только для рейсов на прилёт в базовый аэропорт");
        }
        assertHomeArrivalRequirements(flight, actualArrival);
    }

    /** Прилёт в пункт назначения (SVO → dest), без гейта в SVO. */
    public void assertManualRemoteArrival(Flight flight, String homeIata, LocalDateTime actualArrival) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind != OperationKind.DEPARTURE) {
            throw new BadRequestException(
                    "Ручной прилёт в пункт назначения допустим только для рейсов на вылет из базового аэропорта");
        }
        assertActualArrivalPresent(actualArrival);
        assertAircraftTypeAssigned(flight);
    }

    public void assertAutoTransitionToDeparted(Flight flight, String homeIata, LocalDateTime actualDeparture) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind == OperationKind.ARRIVAL) {
            assertAircraftTypeAssigned(flight);
            assertActualDeparturePresent(actualDeparture);
        } else {
            throw new BadRequestException("Автовылет для outbound рейса выполняется по фактическому времени через планировщик");
        }
    }

    /**
     * Outbound auto-departure: диспетчер заполнил actualDeparture + тип ВС + гейт,
     * планировщик проверяет комплектность и переводит в DEPARTED.
     */
    public void assertOutboundAutoDeparture(Flight flight, String homeIata, LocalDateTime actualDeparture) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind != OperationKind.DEPARTURE) {
            throw new BadRequestException("Outbound-автовылет допустим только для рейсов на вылет из базового аэропорта");
        }
        assertHomeDepartureRequirements(flight, actualDeparture);
    }

    public void assertAutoTransitionToArrived(Flight flight, String homeIata, LocalDateTime actualArrival) {
        OperationKind kind = resolveOperationKind(flight.getSchedule(), homeIata);
        if (kind == OperationKind.ARRIVAL) {
            assertHomeArrivalRequirements(flight, actualArrival);
        } else {
            assertActualDeparturePresent(flight.getActualDeparture());
            assertActualArrivalPresent(actualArrival);
            assertAircraftTypeAssigned(flight);
        }
    }

    private void assertHomeDepartureRequirements(Flight flight, LocalDateTime actualDeparture) {
        assertActualDeparturePresent(actualDeparture);
        assertAircraftTypeAssigned(flight);
        if (flight.getActiveGateAssignment() == null) {
            throw new BadRequestException(
                    "Для перевода в статус DEPARTED необходимо назначить гейт в базовом аэропорту");
        }
    }

    private void assertHomeArrivalRequirements(Flight flight, LocalDateTime actualArrival) {
        assertActualArrivalPresent(actualArrival);
        assertAircraftTypeAssigned(flight);
        if (flight.getActiveGateAssignment() == null) {
            throw new BadRequestException(
                    "Для перевода в статус ARRIVED необходимо назначить гейт в базовом аэропорту");
        }
    }

    private void assertAircraftTypeAssigned(Flight flight) {
        if (flight.getAircraftType() == null) {
            throw new BadRequestException("Необходимо назначить тип воздушного судна");
        }
    }

    private void assertActualDeparturePresent(LocalDateTime actualDeparture) {
        if (actualDeparture == null) {
            throw new BadRequestException(
                    "Для перевода в статус DEPARTED необходимо фактическое время вылета (actualDeparture)");
        }
    }

    private void assertActualArrivalPresent(LocalDateTime actualArrival) {
        if (actualArrival == null) {
            throw new BadRequestException(
                    "Для перевода в статус ARRIVED необходимо фактическое время прилёта (actualArrival)");
        }
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }
}
