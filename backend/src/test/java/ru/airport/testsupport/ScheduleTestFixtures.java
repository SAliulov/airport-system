package ru.airport.testsupport;

import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.PeriodicityType;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.ScheduleRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Сборка шаблона + слота + рейса для интеграционных тестов.
 */
public final class ScheduleTestFixtures {

    private ScheduleTestFixtures() {
    }

    public record SavedSchedule(Schedule schedule, ScheduleSlot slot) {
    }

    public static SavedSchedule saveWeeklySchedule(
            ScheduleRepository scheduleRepository,
            Airline airline,
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departure,
            LocalDateTime arrival) {
        return saveWeeklySchedule(
                scheduleRepository, airline, flightNumber, origin, destination,
                departure, arrival, departure.toLocalDate(), 1);
    }

    public static SavedSchedule saveWeeklySchedule(
            ScheduleRepository scheduleRepository,
            Airline airline,
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departure,
            LocalDateTime arrival,
            LocalDate effectiveFrom,
            int periodicityStep) {
        Schedule schedule = Schedule.builder()
                .flightNumber(flightNumber)
                .originAirport(origin)
                .destinationAirport(destination)
                .effectiveFrom(effectiveFrom)
                .periodicityType(PeriodicityType.WEEKLY)
                .periodicityStep(periodicityStep)
                .isActive(true)
                .airline(airline)
                .build();
        ScheduleSlot slot = ScheduleSlot.builder()
                .dayOfWeek(departure.getDayOfWeek().getValue())
                .departureTime(departure.toLocalTime())
                .arrivalTime(arrival.toLocalTime())
                .schedule(schedule)
                .build();
        schedule.getSlots().add(slot);
        Schedule saved = scheduleRepository.save(schedule);
        return new SavedSchedule(saved, saved.getSlots().getFirst());
    }

    public static Flight saveFlight(
            FlightRepository flightRepository,
            SavedSchedule saved,
            FlightStatus status) {
        return saveFlight(flightRepository, saved, saved.schedule().getEffectiveFrom(), status, null, null);
    }

    public static Flight saveFlight(
            FlightRepository flightRepository,
            SavedSchedule saved,
            LocalDate operationDate,
            FlightStatus status,
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival) {
        Schedule schedule = saved.schedule();
        ScheduleSlot slot = saved.slot();
        LocalTime depTime = slot.getDepartureTime();
        LocalTime arrTime = slot.getArrivalTime();
        LocalDate arrivalDate = operationDate;
        if (!arrTime.isAfter(depTime)) {
            arrivalDate = operationDate.plusDays(1);
        }
        Flight flight = Flight.builder()
                .schedule(schedule)
                .slot(slot)
                .operationDate(operationDate)
                .scheduledDeparture(LocalDateTime.of(operationDate, depTime))
                .scheduledArrival(LocalDateTime.of(arrivalDate, arrTime))
                .status(status)
                .actualDeparture(actualDeparture)
                .actualArrival(actualArrival)
                .build();
        return flightRepository.save(flight);
    }
}
