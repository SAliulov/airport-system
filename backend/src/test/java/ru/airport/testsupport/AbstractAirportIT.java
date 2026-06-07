package ru.airport.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.AirlineRepository;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.scheduler.FlightStatusScheduler;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Общая инфраструктура integration-тестов: Testcontainers PostgreSQL, MockMvc, JWT login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractAirportIT {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final AtomicInteger UNIQUE = new AtomicInteger(100);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("airport.home-iata", () -> "SVO");
        registry.add("airport.timezone", () -> "Europe/Moscow");
    }

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected AirlineRepository airlineRepository;
    @Autowired
    protected AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    protected GateRepository gateRepository;
    @Autowired
    protected ScheduleRepository scheduleRepository;
    @Autowired
    protected FlightRepository flightRepository;
    @Autowired
    protected DelayWarningRepository delayWarningRepository;
    @Autowired
    protected FlightStatusScheduler flightStatusScheduler;

    @SpyBean
    protected SimpMessagingTemplate messagingTemplate;

    protected String dispatcherToken;

    @BeforeEach
    void baseSetUp() throws Exception {
        reset(messagingTemplate);
        dispatcherToken = TestAuthHelper.login(mockMvc, objectMapper, "dispatcher", "dispatcher123");
    }

    protected String bearer() {
        return TestAuthHelper.bearer(dispatcherToken);
    }

    protected String login(String username, String password) throws Exception {
        return TestAuthHelper.login(mockMvc, objectMapper, username, password);
    }

    protected String login(String username, String password, String client) throws Exception {
        return TestAuthHelper.login(mockMvc, objectMapper, username, password, client);
    }

    protected int createFlightForSchedule(
            String flightNumber,
            LocalDateTime departure,
            LocalDateTime arrival,
            int airlineId,
            Integer aircraftTypeId
    ) throws Exception {
        return createFlightForScheduleRoute(
                flightNumber, "SVO", "LED", departure, arrival, airlineId, aircraftTypeId);
    }

    protected int createFlightForScheduleRoute(
            String flightNumber,
            String origin,
            String destination,
            LocalDateTime departure,
            LocalDateTime arrival,
            int airlineId,
            Integer aircraftTypeId
    ) throws Exception {
        JsonNode schedule = postAuthorized("/api/v1/schedules",
                scheduleJson(flightNumber, origin, destination, departure, arrival, airlineId));
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();
        LocalDate operationDate = departure.toLocalDate();

        JsonNode flight = postAuthorized("/api/v1/flights", """
                {"slotId":%d,"operationDate":"%s"}
                """.formatted(slotId, operationDate));
        int flightId = flight.get("flightId").asInt();

        if (aircraftTypeId != null) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .put("/api/v1/flights/{id}/aircraft", flightId)
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"aircraftTypeId":%d}
                                    """.formatted(aircraftTypeId)))
                    .andExpect(status().isOk());
        }
        return flightId;
    }

    protected void transitionOutboundToArrived(
            int flightId,
            int gateId,
            LocalDateTime gateFrom,
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival
    ) throws Exception {
        transitionOutboundToDeparted(flightId, gateId, gateFrom, actualDeparture);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ARRIVED","actualArrival":"%s"}
                                """.formatted(actualArrival)))
                .andExpect(status().isOk());
    }

    protected void transitionInboundToArrived(
            int flightId,
            int gateId,
            LocalDateTime gateFrom,
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival
    ) throws Exception {
        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gateId, gateFrom, actualArrival.plusHours(1))))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DEPARTED","actualDeparture":"%s"}
                                """.formatted(actualDeparture)))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ARRIVED","actualArrival":"%s"}
                                """.formatted(actualArrival)))
                .andExpect(status().isOk());
    }

    protected void transitionOutboundToDeparted(
            int flightId, int gateId, LocalDateTime gateFrom, LocalDateTime actualDeparture) throws Exception {
        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gateId, gateFrom, actualDeparture.plusHours(2))))
                .andExpect(status().isCreated());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DEPARTED","actualDeparture":"%s"}
                                """.formatted(actualDeparture)))
                .andExpect(status().isOk());
    }

    protected JsonNode postAuthorized(String url, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @SafeVarargs
    protected final List<Integer> runConcurrently(Callable<Integer>... tasks)
            throws InterruptedException, ExecutionException {
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.length)) {
            var futures = java.util.Arrays.stream(tasks).map(executor::submit).toList();
            java.util.ArrayList<Integer> result = new java.util.ArrayList<>(futures.size());
            for (var future : futures) {
                result.add(future.get());
            }
            return result;
        }
    }

    protected int postGateAssignmentStatus(Integer flightId, Integer gateId, LocalDateTime from, LocalDateTime to)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gateId, from, to)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    protected static String scheduleJson(
            String flightNumber,
            String originAirport,
            String destinationAirport,
            LocalDateTime departure,
            LocalDateTime arrival,
            int airlineId
    ) {
        LocalDate effectiveFrom = departure.toLocalDate();
        int dow = departure.getDayOfWeek().getValue();
        return """
                {
                  "flightNumber":"%s",
                  "originAirport":"%s",
                  "destinationAirport":"%s",
                  "effectiveFrom":"%s",
                  "periodicityType":"WEEKLY",
                  "periodicityStep":1,
                  "airlineId":%d,
                  "slots":[{"dayOfWeek":%d,"departureTime":"%s","arrivalTime":"%s"}]
                }
                """.formatted(
                flightNumber, originAirport, destinationAirport, effectiveFrom, airlineId,
                dow, departure.toLocalTime(), arrival.toLocalTime());
    }

    protected static String scheduleJsonWithEqualSlotTimes(
            String flightNumber,
            String originAirport,
            String destinationAirport,
            LocalDateTime departure,
            int airlineId
    ) {
        LocalDate effectiveFrom = departure.toLocalDate();
        int dow = departure.getDayOfWeek().getValue();
        LocalTime time = departure.toLocalTime();
        return """
                {
                  "flightNumber":"%s",
                  "originAirport":"%s",
                  "destinationAirport":"%s",
                  "effectiveFrom":"%s",
                  "periodicityType":"WEEKLY",
                  "periodicityStep":1,
                  "airlineId":%d,
                  "slots":[{"dayOfWeek":%d,"departureTime":"%s","arrivalTime":"%s"}]
                }
                """.formatted(
                flightNumber, originAirport, destinationAirport, effectiveFrom, airlineId,
                dow, time, time);
    }

    protected static String uniqueAirlineCode() {
        int value = UNIQUE.incrementAndGet() % 36;
        return "Q" + Character.toUpperCase(Character.forDigit(value, 36));
    }

    protected static String uniqueAircraftCode() {
        return "Q" + Integer.toString(1000 + UNIQUE.incrementAndGet()).substring(1);
    }
}
