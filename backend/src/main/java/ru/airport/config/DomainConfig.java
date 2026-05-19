package ru.airport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.airport.business.AircraftTypeBusinessRules;
import ru.airport.business.AirlineBusinessRules;
import ru.airport.business.DelayWarningBusinessRules;
import ru.airport.business.FlightActualTimeRules;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.business.FlightStatusBusinessRules;
import ru.airport.business.GateAssignmentBusinessRules;
import ru.airport.business.GateBusinessRules;
import ru.airport.business.ScheduleBusinessRules;

@Configuration
public class DomainConfig {

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
    public DelayWarningBusinessRules delayWarningBusinessRules() {
        return new DelayWarningBusinessRules();
    }

    @Bean
    public GateAssignmentBusinessRules gateAssignmentBusinessRules() {
        return new GateAssignmentBusinessRules();
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
}
