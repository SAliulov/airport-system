package ru.airport;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.airport.config.AirportProperties;
import ru.airport.model.Airline;
import ru.airport.repository.AirlineRepository;
import ru.airport.testsupport.TestAuthHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Конфигурация {@code airport.*} не привязана жёстко к SVO в runtime. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class AirportConfigIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("airport.home-iata", () -> "LED");
        registry.add("airport.timezone", () -> "Europe/Moscow");
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    AirportProperties airportProperties;
    @Autowired
    AirlineRepository airlineRepository;

    private String dispatcherToken;

    @BeforeEach
    void setUp() throws Exception {
        dispatcherToken = TestAuthHelper.login(mockMvc, objectMapper, "dispatcher", "dispatcher123");
    }

    @Test
    void airportConfigEndpoint_returnsYamlValues() throws Exception {
        mockMvc.perform(get("/api/v1/airport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeIata").value("LED"))
                .andExpect(jsonPath("$.timezone").value("Europe/Moscow"))
                .andExpect(jsonPath("$.gatePlanWindowHours").isNumber())
                .andExpect(jsonPath("$.gatePostGraceMinutes").isNumber());
        assertThat(airportProperties.getHomeIata()).isEqualTo("LED");
    }

    @Test
    void scheduleCreate_acceptsRouteThroughConfiguredHomeAirport() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber":"LED1",
                                  "originAirport":"LED",
                                  "destinationAirport":"SVO",
                                  "effectiveFrom":"2026-07-01",
                                  "periodicityType":"WEEKLY",
                                  "periodicityStep":1,
                                  "airlineId":%d,
                                  "slots":[{"dayOfWeek":2,"departureTime":"08:00","arrivalTime":"10:00"}]
                                }
                                """.formatted(airline.getAirlineId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originAirport").value("LED"));
    }
}
