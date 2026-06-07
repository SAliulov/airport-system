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
import ru.airport.testsupport.ScheduleTestFixtures;

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
        LocalDateTime dep = LocalDateTime.of(2026, 3, 29, 10, 0);
        LocalDateTime arr = LocalDateTime.of(2026, 3, 29, 11, 0);
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, su, "SU999", "SVO", "LED", dep, arr);

        Flight scheduled = ScheduleTestFixtures.saveFlight(
                flightRepository, saved, FlightStatus.SCHEDULED);
        Flight departed = ScheduleTestFixtures.saveFlight(
                flightRepository, saved, LocalDate.of(2026, 4, 5), FlightStatus.DEPARTED,
                LocalDateTime.of(2026, 4, 5, 10, 5), null);
        Flight arrived = ScheduleTestFixtures.saveFlight(
                flightRepository, saved, LocalDate.of(2026, 4, 12), FlightStatus.ARRIVED, null, null);

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
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, su, "SU700", "SVO", "LED", dep, arr);
        Flight flight = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);

        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.plusDays(1).atStartOfDay();

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.SCHEDULED, null, null, null, null, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, FlightStatus.ARRIVED, null, null, null, null, null)))
                .extracting(Flight::getFlightId)
                .doesNotContain(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(start, end, null, su.getAirlineId(), null, null, null, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());

        assertThat(flightRepository.findAll(FlightSpecifications.forApiList(null, null, FlightStatus.SCHEDULED, null, null, null, null, null)))
                .extracting(Flight::getFlightId)
                .contains(flight.getFlightId());
    }

    @Test
    void existsBySlotAndOperationDate_enforcesOneInstancePerSlotAndDate() {
        Airline su = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.of(2026, 8, 1);
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, su, "SU800", "SVO", "LED",
                day.atTime(9, 0), day.atTime(11, 0));
        ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);

        assertThat(flightRepository.existsBySlot_SlotIdAndOperationDate(saved.slot().getSlotId(), day))
                .isTrue();
    }
}
