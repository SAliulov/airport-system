package ru.airport.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.Schedule;
import ru.airport.model.SizeCategory;
import ru.airport.testsupport.DockerConditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class AirportJpaRepositoriesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private GateRepository gateRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;

    /**
     * Регрессия: при STRING-enum нельзя сравнивать max_size_category как строку &gt;= —
     * иначе JUMBO &lt; NARROW лексикографически и гейт B2 выпадает для NARROW ВС.
     */
    @Test
    void findActiveGatesCompatibleWithAircraftSize_matchesSizeCategorySemantics() {
        assertThat(gateRepository.findActiveGatesCompatibleWithAircraftSize(SizeCategory.NARROW))
                .extracting(Gate::getGateNumber)
                .contains("101", "105", "121");

        assertThat(gateRepository.findActiveGatesCompatibleWithAircraftSize(SizeCategory.WIDE))
                .extracting(Gate::getGateNumber)
                .contains("106", "126")
                .doesNotContain("101", "121");

        assertThat(gateRepository.findActiveGatesCompatibleWithAircraftSize(SizeCategory.JUMBO))
                .extracting(Gate::getGateNumber)
                .containsExactly("111");
    }

    @Test
    void findForAutoStatusProcessing_loadsScheduleForActiveStatuses() {
        Airline su = airlineRepository.findByIataCode("SU").orElseThrow();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("SU999")
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(LocalDateTime.of(2026, 3, 29, 10, 0))
                .scheduledArrival(LocalDateTime.of(2026, 3, 29, 11, 0))
                .airline(su)
                .build());

        Flight scheduled = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build());
        Flight departed = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.DEPARTED)
                .actualDeparture(LocalDateTime.of(2026, 3, 29, 10, 5))
                .build());
        Flight arrived = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.ARRIVED)
                .build());

        assertThat(flightRepository.findForAutoStatusProcessing(
                EnumSet.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.DEPARTED)))
                .extracting(Flight::getFlightId)
                .contains(scheduled.getFlightId(), departed.getFlightId())
                .doesNotContain(arrived.getFlightId());
    }

    @Test
    void forApiList_specification_filtersByDayStatusAndAirline_withoutJpqlNullOrBug() {
        Airline su = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.of(2026, 7, 15);
        LocalDateTime dep = day.atTime(8, 0);
        LocalDateTime arr = day.atTime(10, 0);
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("SU700")
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(dep)
                .scheduledArrival(arr)
                .airline(su)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build());

        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.SCHEDULED, null, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.ARRIVED, null, null)))
                .extracting(Flight::getFlightId)
                .doesNotContain(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, null, su.getAirlineId(), null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(null, null, FlightStatus.SCHEDULED, null, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());
    }
}
