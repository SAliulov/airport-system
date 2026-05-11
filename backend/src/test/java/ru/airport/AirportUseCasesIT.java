package ru.airport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentMatchers;
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
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.Schedule;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.AirlineRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.scheduler.FlightStatusScheduler;
import ru.airport.testsupport.DockerConditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class AirportUseCasesIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final AtomicInteger UNIQUE = new AtomicInteger(100);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AirlineRepository airlineRepository;
    @Autowired
    private AircraftTypeRepository aircraftTypeRepository;
    @Autowired
    private GateRepository gateRepository;
    @Autowired
    private ScheduleRepository scheduleRepository;
    @Autowired
    private FlightRepository flightRepository;
    @Autowired
    private FlightStatusScheduler flightStatusScheduler;

    @SpyBean
    private SimpMessagingTemplate messagingTemplate;

    private String dispatcherToken;

    @BeforeEach
    void setUp() throws Exception {
        reset(messagingTemplate);
        dispatcherToken = login("dispatcher", "dispatcher123");
    }

    @Test
    void useCase1_directoryCrud_forAirlinesAndAircraftTypes() throws Exception {
        String airlineCode = uniqueAirlineCode();
        JsonNode airline = postAuthorized("/api/v1/airlines", """
                {"iataCode":"%s","name":"QA Test Air","country":"RU"}
                """.formatted(airlineCode));
        int airlineId = airline.get("airlineId").asInt();

        mockMvc.perform(put("/api/v1/airlines/{id}", airlineId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iataCode":"%s","name":"QA Test Air Updated","country":"KZ"}
                                """.formatted(airlineCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("QA Test Air Updated"));

        mockMvc.perform(delete("/api/v1/airlines/{id}", airlineId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(airlineRepository.findById(airlineId)).isEmpty();

        String icaoCode = uniqueAircraftCode();
        JsonNode aircraft = postAuthorized("/api/v1/aircraft-types", """
                {"icaoCode":"%s","passengerCapacity":120,"sizeCategory":"NARROW"}
                """.formatted(icaoCode));
        int aircraftId = aircraft.get("aircraftTypeId").asInt();

        mockMvc.perform(put("/api/v1/aircraft-types/{id}", aircraftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"icaoCode":"%s","passengerCapacity":140,"sizeCategory":"NARROW"}
                                """.formatted(icaoCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passengerCapacity").value(140));

        mockMvc.perform(delete("/api/v1/aircraft-types/{id}", aircraftId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(aircraftTypeRepository.findById(aircraftId)).isEmpty();
    }

    @Test
    void useCase2_scheduleAndFlightCrud_filter_search_and_update() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(5);

        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson("UT100", "SVO", "LED",
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();

        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("UT101", "SVO", "KZN",
                                day.atTime(11, 0), day.atTime(13, 0), airline.getAirlineId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("UT101"));

        JsonNode alternateSchedule = postAuthorized("/api/v1/schedules", scheduleJson("UT202", "LED", "AER",
                day.atTime(14, 0), day.atTime(16, 0), airline.getAirlineId()));
        int alternateScheduleId = alternateSchedule.get("scheduleId").asInt();

        JsonNode createdFlight = postAuthorized("/api/v1/flights", """
                {"scheduleId":%d}
                """.formatted(scheduleId));
        int flightId = createdFlight.get("flightId").asInt();

        mockMvc.perform(put("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":%d}
                                """.formatted(alternateScheduleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.scheduleId").value(alternateScheduleId));

        mockMvc.perform(get("/api/v1/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].flightId").isArray());

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("date", day.toString())
                        .param("airline", String.valueOf(airline.getAirlineId()))
                        .param("direction", "AER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).exists());

        mockMvc.perform(get("/api/v1/flights/search")
                        .param("query", "UT202"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schedule.flightNumber").value("UT202"));

        mockMvc.perform(get("/api/v1/schedules/search")
                        .param("query", "UT20")
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("UT202"));

        mockMvc.perform(delete("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(flightRepository.findById(flightId)).isEmpty();
    }

    @Test
    void useCase3_schedulerAutomaticallyUpdatesStatusesAndPublishesEvents() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        Schedule departureSchedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("SCH" + UNIQUE.incrementAndGet())
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(LocalDateTime.now().minusMinutes(30))
                .scheduledArrival(LocalDateTime.now().plusHours(1))
                .airline(airline)
                .build());
        Flight departureFlight = flightRepository.save(Flight.builder()
                .schedule(departureSchedule)
                .status(FlightStatus.SCHEDULED)
                .build());

        Schedule arrivalSchedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("ARR" + UNIQUE.incrementAndGet())
                .originAirport("LED")
                .destinationAirport("SVO")
                .scheduledDeparture(LocalDateTime.now().minusHours(3))
                .scheduledArrival(LocalDateTime.now().minusMinutes(10))
                .airline(airline)
                .build());
        Flight arrivalFlight = flightRepository.save(Flight.builder()
                .schedule(arrivalSchedule)
                .status(FlightStatus.DEPARTED)
                .build());

        reset(messagingTemplate);
        flightStatusScheduler.updateFlightStatuses();

        Flight updatedDeparture = flightRepository.findById(departureFlight.getFlightId()).orElseThrow();
        Flight updatedArrival = flightRepository.findById(arrivalFlight.getFlightId()).orElseThrow();

        assertThat(updatedDeparture.getStatus()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(updatedDeparture.getActualDeparture()).isNotNull();
        assertThat(updatedArrival.getStatus()).isEqualTo(FlightStatus.ARRIVED);
        assertThat(updatedArrival.getActualArrival()).isNotNull();
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/flights"), ArgumentMatchers.any(Object.class));
    }

    @Test
    void useCase4_gateAssignment_detectsConflicts_andUseCase7_timelineReturnsSegments() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("A1").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(7);

        int flightOneId = createFlightForSchedule("GT" + UNIQUE.incrementAndGet(),
                day.atTime(9, 0), day.atTime(11, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());
        int flightTwoId = createFlightForSchedule("GT" + UNIQUE.incrementAndGet(),
                day.atTime(9, 30), day.atTime(11, 30), airline.getAirlineId(), aircraft.getAircraftTypeId());

        reset(messagingTemplate);
        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightOneId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(8, 45), day.atTime(11, 15))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gate.gateNumber").value("A1"));
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/gate-changes"), ArgumentMatchers.any(Object.class));

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightTwoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(9, 15), day.atTime(10, 15))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/gates/timeline").param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightId").value(flightOneId))
                .andExpect(jsonPath("$[0].gateNumber").value("A1"));
    }

    @Test
    void useCase5_assignAircraftChecksGateCompatibility() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("B1").orElseThrow();
        AircraftType wideAircraft = aircraftTypeRepository.findByIcaoCode("B77W").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(9);

        int flightId = createFlightForSchedule("AC" + UNIQUE.incrementAndGet(),
                day.atTime(6, 0), day.atTime(8, 0), airline.getAirlineId(), null);

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(5, 45), day.atTime(8, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/flights/{id}/aircraft", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"aircraftTypeId":%d}
                                """.formatted(wideAircraft.getAircraftTypeId())))
                .andExpect(status().isConflict());
    }

    @Test
    void useCase6_exportProducesPdfAndExcelForDispatcher() throws Exception {
        LocalDate day = LocalDate.now().plusDays(11);
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        createFlightForSchedule("EX" + UNIQUE.incrementAndGet(),
                day.atTime(7, 0), day.atTime(9, 0), airline.getAirlineId(), null);

        mockMvc.perform(get("/api/v1/schedules/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());

        mockMvc.perform(get("/api/v1/schedules/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isNotEmpty());
    }

    @Test
    void useCase8_delayWarningsAreCreatedListedAndPublished() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(13);
        int flightId = createFlightForSchedule("DL" + UNIQUE.incrementAndGet(),
                day.atTime(15, 0), day.atTime(17, 0), airline.getAirlineId(), null);

        reset(messagingTemplate);
        mockMvc.perform(post("/api/v1/flights/{id}/delay-warnings", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delayMinutes":35,"reason":"Weather"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delayMinutes").value(35))
                .andExpect(jsonPath("$.reason").value("Weather"));

        mockMvc.perform(get("/api/v1/flights/{id}/delay-warnings", flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].delayMinutes").value(35));

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/delays"), ArgumentMatchers.any(Object.class));
    }

    private int createFlightForSchedule(
            String flightNumber,
            LocalDateTime departure,
            LocalDateTime arrival,
            int airlineId,
            Integer aircraftTypeId
    ) throws Exception {
        JsonNode schedule = postAuthorized("/api/v1/schedules",
                scheduleJson(flightNumber, "SVO", "LED", departure, arrival, airlineId));
        int scheduleId = schedule.get("scheduleId").asInt();

        JsonNode flight = postAuthorized("/api/v1/flights", """
                {"scheduleId":%d}
                """.formatted(scheduleId));
        int flightId = flight.get("flightId").asInt();

        if (aircraftTypeId != null) {
            mockMvc.perform(put("/api/v1/flights/{id}/aircraft", flightId)
                            .header(HttpHeaders.AUTHORIZATION, bearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"aircraftTypeId":%d}
                                    """.formatted(aircraftTypeId)))
                    .andExpect(status().isOk());
        }
        return flightId;
    }

    private JsonNode postAuthorized(String url, String json) throws Exception {
        MvcResult result = mockMvc.perform(post(url)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + dispatcherToken;
    }

    private static String scheduleJson(
            String flightNumber,
            String originAirport,
            String destinationAirport,
            LocalDateTime departure,
            LocalDateTime arrival,
            int airlineId
    ) {
        return """
                {
                  "flightNumber":"%s",
                  "originAirport":"%s",
                  "destinationAirport":"%s",
                  "scheduledDeparture":"%s",
                  "scheduledArrival":"%s",
                  "airlineId":%d
                }
                """.formatted(flightNumber, originAirport, destinationAirport, departure, arrival, airlineId);
    }

    private static String uniqueAirlineCode() {
        int value = UNIQUE.incrementAndGet() % 36;
        return "Q" + Character.toUpperCase(Character.forDigit(value, 36));
    }

    private static String uniqueAircraftCode() {
        return "Q" + Integer.toString(1000 + UNIQUE.incrementAndGet()).substring(1);
    }
}
