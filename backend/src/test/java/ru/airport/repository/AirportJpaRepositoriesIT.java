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
                .contains("A1", "A2", "B1", "B2");

        assertThat(gateRepository.findActiveGatesCompatibleWithAircraftSize(SizeCategory.WIDE))
                .extracting(Gate::getGateNumber)
                .contains("A2", "B2")
                .doesNotContain("A1", "B1");

        assertThat(gateRepository.findActiveGatesCompatibleWithAircraftSize(SizeCategory.JUMBO))
                .extracting(Gate::getGateNumber)
                .containsExactly("B2");
    }

    @Test
    void findReadyToDeparture_andArrive_useEnumBindParameters() {
        Airline su = airlineRepository.findByIataCode("SU").orElseThrow();
        Schedule schedule = Schedule.builder()
                .flightNumber("SU999")
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(LocalDateTime.of(2026, 3, 29, 10, 0))
                .scheduledArrival(LocalDateTime.of(2026, 3, 29, 11, 0))
                .airline(su)
                .build();
        schedule = scheduleRepository.save(schedule);

        Flight flight = Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build();
        flight = flightRepository.save(flight);

        LocalDateTime afterDeparture = LocalDateTime.of(2026, 3, 29, 10, 30);
        assertThat(flightRepository.findReadyToDeparture(afterDeparture))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        flight.setStatus(FlightStatus.DEPARTED);
        flightRepository.save(flight);

        LocalDateTime afterArrival = LocalDateTime.of(2026, 3, 29, 11, 30);
        assertThat(flightRepository.findReadyToArrive(afterArrival))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());
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

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.SCHEDULED, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.ARRIVED, null)))
                .extracting(Flight::getFlightId)
                .doesNotContain(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, null, su.getAirlineId())))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(null, null, FlightStatus.SCHEDULED, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());
    }
}
