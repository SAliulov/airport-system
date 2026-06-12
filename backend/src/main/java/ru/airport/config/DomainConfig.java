package ru.airport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.airport.business.AircraftTypeBusinessRules;
import ru.airport.business.AirlineBusinessRules;
import ru.airport.business.DelayWarningBusinessRules;
import ru.airport.business.FlightAutoStatusBusinessRules;
import ru.airport.business.FlightActualTimeRules;
import ru.airport.business.FlightGenerationBusinessRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.business.FlightPlanningTimeBusinessRules;
import ru.airport.business.FlightStatusBusinessRules;
import ru.airport.business.GateAssignmentBusinessRules;
import ru.airport.business.GateBusinessRules;
import ru.airport.business.ScheduleBusinessRules;
import ru.airport.business.ScheduleOccurrenceBusinessRules;
import ru.airport.business.SchedulePeriodicityBusinessRules;
import ru.airport.business.ScheduleSlotBusinessRules;

@Configuration
public class DomainConfig {

    @Bean
    public FlightAutoStatusBusinessRules flightAutoStatusBusinessRules() {
        return new FlightAutoStatusBusinessRules();
    }

    @Bean
    public FlightStatusBusinessRules flightStatusBusinessRules() {
        return new FlightStatusBusinessRules();
    }

    @Bean
    public FlightActualTimeRules flightActualTimeRules(AirportProperties airportProperties) {
        return new FlightActualTimeRules(airportProperties.getScheduler().getMaxActualTimeFutureSkewMinutes());
    }

    @Bean
    public FlightMutationBusinessRules flightMutationBusinessRules() {
        return new FlightMutationBusinessRules();
    }

    @Bean
    public FlightPlanningTimeBusinessRules flightPlanningTimeBusinessRules() {
        return new FlightPlanningTimeBusinessRules();
    }

    @Bean
    public DelayWarningBusinessRules delayWarningBusinessRules() {
        return new DelayWarningBusinessRules();
    }

    @Bean
    public GateAssignmentBusinessRules gateAssignmentBusinessRules(AirportProperties airportProperties) {
        return new GateAssignmentBusinessRules(
                airportProperties.getGatePlanWindowHours(),
                airportProperties.getGatePostGraceMinutes());
    }

    @Bean
    public AirlineBusinessRules airlineBusinessRules() {
        return new AirlineBusinessRules();
    }

    @Bean
    public AircraftTypeBusinessRules aircraftTypeBusinessRules() {
        return new AircraftTypeBusinessRules();
    }

    @Bean
    public GateBusinessRules gateBusinessRules() {
        return new GateBusinessRules();
    }

    @Bean
    public ScheduleBusinessRules scheduleBusinessRules() {
        return new ScheduleBusinessRules();
    }

    @Bean
    public SchedulePeriodicityBusinessRules schedulePeriodicityBusinessRules() {
        return new SchedulePeriodicityBusinessRules();
    }

    @Bean
    public ScheduleOccurrenceBusinessRules scheduleOccurrenceBusinessRules(
            SchedulePeriodicityBusinessRules schedulePeriodicityBusinessRules) {
        return new ScheduleOccurrenceBusinessRules(schedulePeriodicityBusinessRules);
    }

    @Bean
    public ScheduleSlotBusinessRules scheduleSlotBusinessRules(
            ScheduleOccurrenceBusinessRules scheduleOccurrenceBusinessRules) {
        return new ScheduleSlotBusinessRules(scheduleOccurrenceBusinessRules);
    }

    @Bean
    public FlightHomeAirportRules flightHomeAirportRules() {
        return new FlightHomeAirportRules();
    }

    @Bean
    public FlightGenerationBusinessRules flightGenerationBusinessRules(
            SchedulePeriodicityBusinessRules schedulePeriodicityBusinessRules,
            ScheduleOccurrenceBusinessRules scheduleOccurrenceBusinessRules) {
        return new FlightGenerationBusinessRules(schedulePeriodicityBusinessRules, scheduleOccurrenceBusinessRules);
    }
}
