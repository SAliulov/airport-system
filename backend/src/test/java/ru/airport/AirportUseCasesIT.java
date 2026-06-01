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
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.repository.AirlineRepository;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.GateRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.scheduler.FlightStatusScheduler;
import ru.airport.service.FlightAutoStatusService;
import ru.airport.testsupport.ScheduleTestFixtures;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
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
        registry.add("airport.home-iata", () -> "SVO");
        registry.add("airport.timezone", () -> "Europe/Moscow");
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
    private DelayWarningRepository delayWarningRepository;
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

    // ═══ UC1: Справочники (airline, aircraft_type) ═══

    @Test
    void useCase1_directoryCrud_forAirlinesAndAircraftTypes() throws Exception {
        String airlineCode = uniqueAirlineCode();
        JsonNode airline = postAuthorized("/api/v1/airlines", """
                {"iataCode":"%s","name":"QA Test Air","country":"RU"}
                """.formatted(airlineCode));
        int airlineId = airline.get("airlineId").asInt();

        mockMvc.perform(post("/api/v1/airlines")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"iataCode":"%s","name":"Duplicate Air","country":"RU"}
                                """.formatted(airlineCode)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("IATA-код уже занят: " + airlineCode));

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
    void scheduleUpdate_withLinkedFlights_preservesSlotsWhenSlotIdSent() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.of(2026, 6, 2);

        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson("UT777", "SVO", "LED",
                day.atTime(8, 0), day.atTime(10, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();

        postAuthorized("/api/v1/flights", """
                {"slotId":%d,"operationDate":"%s"}
                """.formatted(slotId, day));

        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber":"UT777",
                                  "originAirport":"SVO",
                                  "destinationAirport":"LED",
                                  "effectiveFrom":"%s",
                                  "isActive":true,
                                  "periodicityType":"WEEKLY",
                                  "periodicityStep":1,
                                  "airlineId":%d,
                                  "slots":[{"slotId":%d,"dayOfWeek":2,"departureTime":"08:00","arrivalTime":"10:00"}]
                                }
                                """.formatted(day, airline.getAirlineId(), slotId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.slots[0].slotId").value(slotId));
    }

    @Test
    void scheduleUpdate_rejectsDeactivationWhenLinkedFlightsExist() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.of(2026, 6, 3);

        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson("UT778", "SVO", "LED",
                day.atTime(8, 0), day.atTime(10, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();

        postAuthorized("/api/v1/flights", """
                {"slotId":%d,"operationDate":"%s"}
                """.formatted(slotId, day));

        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber":"UT778",
                                  "originAirport":"SVO",
                                  "destinationAirport":"LED",
                                  "effectiveFrom":"%s",
                                  "isActive":false,
                                  "periodicityType":"WEEKLY",
                                  "periodicityStep":1,
                                  "airlineId":%d,
                                  "slots":[{"slotId":%d,"dayOfWeek":2,"departureTime":"08:00","arrivalTime":"10:00"}]
                                }
                                """.formatted(day, airline.getAirlineId(), slotId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Нельзя деактивировать шаблон: по нему уже созданы рейсы. Удалите рейсы или оставьте шаблон активным."));
    }

    // ═══ UC2: Расписание и рейсы (CRUD, filter, search) ═══

    @Test
    void useCase2_scheduleAndFlightCrud_filter_search_and_update() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(5);

        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson("UT100", "SVO", "LED",
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();

        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson("UT101", "SVO", "KZN",
                                day.atTime(11, 0), day.atTime(13, 0), airline.getAirlineId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("UT101"));

        JsonNode alternateSchedule = postAuthorized("/api/v1/schedules", scheduleJson("UT202", "AER", "SVO",
                day.atTime(14, 0), day.atTime(16, 0), airline.getAirlineId()));
        int alternateSlotId = alternateSchedule.get("slots").get(0).get("slotId").asInt();

        JsonNode createdFlight = postAuthorized("/api/v1/flights", """
                {"slotId":%d,"operationDate":"%s"}
                """.formatted(slotId, day));
        int flightId = createdFlight.get("flightId").asInt();

        mockMvc.perform(put("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":%d,"operationDate":"%s"}
                                """.formatted(alternateSlotId, day)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Изменение слота у существующего рейса запрещено"));

        mockMvc.perform(put("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":%d,"operationDate":"%s"}
                                """.formatted(slotId, day)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.scheduleId").value(scheduleId));

        mockMvc.perform(get("/api/v1/flights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].flightId").isArray());

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("date", day.toString())
                        .param("airline", String.valueOf(airline.getAirlineId()))
                        .param("direction", "SVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).exists());

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("origin", "SVO")
                        .param("destination", "KZN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).exists());

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("origin", "AER")
                        .param("destination", "SVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).isEmpty());

        mockMvc.perform(get("/api/v1/flights/search")
                        .param("query", "UT101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schedule.flightNumber").value("UT101"));

        mockMvc.perform(get("/api/v1/schedules/search")
                        .param("query", "UT20")
                        .param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("UT202"));

        mockMvc.perform(delete("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя удалить расписание: есть связанные рейсы."));

        mockMvc.perform(delete("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNoContent());
        assertThat(flightRepository.findById(flightId)).isEmpty();
    }

    // ═══ UC3: Автообновление статуса (@Scheduled) ═══
    // SCHEDULED→DELAYED/DEPARTED→ARRIVED; без ВС/гейта переходы пропускаются.

    @Test
    void useCase3_schedulerAutomaticallyUpdatesStatusesAndPublishesEvents() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDateTime depOut = LocalDateTime.now().minusMinutes(30);
        LocalDateTime arrOut = LocalDateTime.now().plusHours(1);
        var departureSaved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "SCH" + UNIQUE.incrementAndGet(),
                "SVO", "LED", depOut, arrOut);
        AircraftType a320 = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate departureGate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDateTime depFrom = LocalDateTime.now().minusHours(1);
        LocalDateTime depTo = LocalDateTime.now().plusHours(2);
        Flight departureFlight = ScheduleTestFixtures.saveFlight(
                flightRepository, departureSaved, FlightStatus.SCHEDULED);
        departureFlight.setAircraftType(a320);
        departureFlight.getGateAssignments().add(GateAssignment.builder()
                .flight(departureFlight)
                .gate(departureGate)
                .assignedFrom(depFrom)
                .assignedTo(depTo)
                .build());
        departureFlight = flightRepository.save(departureFlight);

        LocalDateTime depIn = LocalDateTime.now().minusHours(3);
        LocalDateTime arrIn = LocalDateTime.now().minusMinutes(10);
        var arrivalSaved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "ARR" + UNIQUE.incrementAndGet(),
                "LED", "SVO", depIn, arrIn);
        Gate arrivalGate = gateRepository.findByGateNumber("126").orElseThrow();
        Flight arrivalFlight = ScheduleTestFixtures.saveFlight(
                flightRepository, arrivalSaved, FlightStatus.SCHEDULED);
        arrivalFlight.setAircraftType(a320);
        arrivalFlight.getGateAssignments().add(GateAssignment.builder()
                .flight(arrivalFlight)
                .gate(arrivalGate)
                .assignedFrom(LocalDateTime.now().minusHours(3))
                .assignedTo(LocalDateTime.now().plusHours(1))
                .build());
        arrivalFlight = flightRepository.save(arrivalFlight);

        reset(messagingTemplate);
        flightStatusScheduler.updateFlightStatuses();

        Flight updatedDeparture = flightRepository.findById(departureFlight.getFlightId()).orElseThrow();
        Flight updatedArrival = flightRepository.findById(arrivalFlight.getFlightId()).orElseThrow();

        assertThat(updatedDeparture.getStatus()).isEqualTo(FlightStatus.DELAYED);
        assertThat(updatedDeparture.getActualDeparture()).isNull();
        assertThat(updatedArrival.getStatus()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(updatedArrival.getActualDeparture()).isEqualTo(updatedArrival.getScheduledDeparture());
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/flights"), ArgumentMatchers.any(Object.class));
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(ArgumentMatchers.eq("/topic/operational-events"), ArgumentMatchers.any(Object.class));
    }

    @Test
    void operationalEvents_publishedOnDispatcherStatusChange() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(14);
        int flightId = createFlightForSchedule("OE" + UNIQUE.incrementAndGet(),
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId(), null);

        reset(messagingTemplate);
        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELAYED\"}"))
                .andExpect(status().isOk());

        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/operational-events"), ArgumentMatchers.any(Object.class));
    }

    @Test
    void useCase4_gateAssignment_detectsConflicts() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
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
                .andExpect(jsonPath("$.gate.gateNumber").value("101"));
        verify(messagingTemplate, times(1))
                .convertAndSend(ArgumentMatchers.eq("/topic/gate-changes"), ArgumentMatchers.any(Object.class));

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightTwoId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(9, 15), day.atTime(10, 15))))
                .andExpect(status().isConflict());
    }

    @Test
    void useCase4_gateAssignment_concurrentRequestsAllowSingleWinner() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(8);

        int flightOneId = createFlightForSchedule("GC" + UNIQUE.incrementAndGet(),
                day.atTime(12, 0), day.atTime(14, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());
        int flightTwoId = createFlightForSchedule("GC" + UNIQUE.incrementAndGet(),
                day.atTime(12, 15), day.atTime(14, 15), airline.getAirlineId(), aircraft.getAircraftTypeId());

        reset(messagingTemplate);
        List<Integer> statuses = runConcurrently(
                () -> postGateAssignmentStatus(flightOneId, gate.getGateId(), day.atTime(11, 45), day.atTime(14, 5)),
                () -> postGateAssignmentStatus(flightTwoId, gate.getGateId(), day.atTime(12, 0), day.atTime(13, 30))
        );

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        verify(messagingTemplate, times(1))
                .convertAndSend(ArgumentMatchers.eq("/topic/gate-changes"), ArgumentMatchers.any(Object.class));
    }

    @Test
    void useCase4_gateReassignment_closesPriorAssignmentAndAllowsSameFlightInterval() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate101 = gateRepository.findByGateNumber("101").orElseThrow();
        Gate gate102 = gateRepository.findByGateNumber("102").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(12);

        int flightId = createFlightForSchedule("GR" + UNIQUE.incrementAndGet(),
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate101.getGateId(), day.atTime(9, 45), day.atTime(12, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate101.getGateId(), day.atTime(9, 45), day.atTime(12, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate102.getGateId(), day.atTime(10, 0), day.atTime(12, 30))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/flights/{id}", flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGateAssignment.gate.gateNumber").value("102"));

        mockMvc.perform(get("/api/v1/gates/timeline").param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.gateNumber=='101' && @.flightId==" + flightId + ")].assignedTo")
                        .value(day.atTime(10, 0).toString()));
    }

    @Test
    void useCase4_cancelledFlight_closesActiveGateAssignment() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(13);

        int flightId = createFlightForSchedule("GC" + UNIQUE.incrementAndGet(),
                day.atTime(14, 0), day.atTime(16, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(13, 45), day.atTime(16, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/flights/{id}", flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentGateAssignment.assignedTo").exists());
    }

    // ═══ UC5: Назначение типа ВС (совместимость с гейтом) ═══

    @Test
    void useCase5_assignAircraftChecksGateCompatibility() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
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
    void useCase5_compatibleAircraftTypes_filteredByAssignedGate() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(10);

        int flightId = createFlightForSchedule("CA" + UNIQUE.incrementAndGet(),
                day.atTime(5, 0), day.atTime(7, 0), airline.getAirlineId(), null);

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(4, 45), day.atTime(7, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/flights/{id}/compatible-aircraft-types", flightId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.icaoCode=='A320')]").exists())
                .andExpect(jsonPath("$[?(@.icaoCode=='B77W')]").isEmpty());
    }

    // ═══ UC6: Экспорт PDF/Excel ═══

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
    void useCase6_exportForbiddenForReadOnlyUser() throws Exception {
        String readerToken = login("reader", "reader123", "MOBILE");
        LocalDate day = LocalDate.now().plusDays(11);

        mockMvc.perform(get("/api/v1/schedules/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .param("date", day.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/schedules/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .param("date", day.toString()))
                .andExpect(status().isForbidden());
    }

    // ═══ UC7: Timeline гейтов ═══

    @Test
    void useCase7_timeline_returnsSegmentsWithFlightStatus() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(14);

        int flightId = createFlightForSchedule("TL" + UNIQUE.incrementAndGet(),
                day.atTime(16, 0), day.atTime(18, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(15, 45), day.atTime(18, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/gates/timeline").param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId==%d)]".formatted(flightId)).exists())
                .andExpect(jsonPath("$[?(@.flightId==%d)].gateNumber".formatted(flightId)).value("101"))
                .andExpect(jsonPath("$[?(@.flightId==%d)].flightStatus".formatted(flightId)).value("SCHEDULED"));
    }

    // ═══ Дополнительно: auth, RBAC, инварианты статусов ═══

    @Test
    void login_withCyrillicUsername_succeeds() throws Exception {
        String token = login("Диспетчер_Женя1987", "eugene_egov_is_cool_dispatcher_kotcheshir777", "DISPATCHER");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Диспетчер_Женя1987"))
                .andExpect(jsonPath("$.role").value("DISPATCHER"));
    }

    @Test
    void login_readOnlyUser_withDispatcherClient_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"reader","password":"reader123","client":"DISPATCHER"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Неверное имя пользователя или пароль"));
    }

    @Test
    void login_dispatcherUser_withMobileClient_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"dispatcher","password":"dispatcher123","client":"MOBILE"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Неверное имя пользователя или пароль"));
    }

    @Test
    void login_readOnlyUser_withMobileClient_succeeds() throws Exception {
        String token = login("reader", "reader123", "MOBILE");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("READ_ONLY"));
    }

    @Test
    void arrivedOutbound_correctActualTimes_updatesDepartureAndArrival() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(24);
        LocalDateTime dep = day.atTime(8, 0);
        LocalDateTime arr = day.atTime(10, 0);

        int flightId = createFlightForScheduleRoute(
                "OB" + UNIQUE.incrementAndGet(), "SVO", "LED", dep, arr,
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionOutboundToArrived(flightId, gate.getGateId(), day.atTime(7, 45), day.atTime(8, 5), day.atTime(10, 5));

        LocalDateTime correctedDep = day.atTime(8, 10);
        LocalDateTime correctedArr = day.atTime(10, 20);

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualDeparture":"%s","actualArrival":"%s"}
                                """.formatted(correctedDep, correctedArr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARRIVED"))
                .andExpect(jsonPath("$.actualDeparture").value(correctedDep.toString()))
                .andExpect(jsonPath("$.actualArrival").value(correctedArr.toString()));
    }

    @Test
    void arrivedInbound_correctActualTimes_updatesArrival() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("126").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(25);
        LocalDateTime dep = day.atTime(6, 0);
        LocalDateTime arr = day.atTime(8, 0);

        int flightId = createFlightForScheduleRoute(
                "IB" + UNIQUE.incrementAndGet(), "LED", "SVO", dep, arr,
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionInboundToArrived(flightId, gate.getGateId(), day.atTime(5, 45), day.atTime(6, 5), day.atTime(8, 3));

        LocalDateTime correctedArr = day.atTime(8, 15);

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualArrival":"%s"}
                                """.formatted(correctedArr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualArrival").value(correctedArr.toString()))
                .andExpect(jsonPath("$.actualDeparture").value(day.atTime(6, 5).toString()));
    }

    @Test
    void correctActualTimes_rejectsInvalidStatusOrInterval() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(26);
        LocalDateTime dep = day.atTime(9, 0);
        LocalDateTime arr = day.atTime(11, 0);

        int scheduledId = createFlightForScheduleRoute(
                "SC" + UNIQUE.incrementAndGet(), "SVO", "LED", dep, arr,
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        int departedId = createFlightForScheduleRoute(
                "DP" + UNIQUE.incrementAndGet(), "SVO", "LED",
                day.atTime(12, 0), day.atTime(14, 0),
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionOutboundToDeparted(departedId, gate.getGateId(), day.atTime(11, 45), day.atTime(12, 5));

        int arrivedId = createFlightForScheduleRoute(
                "AR" + UNIQUE.incrementAndGet(), "SVO", "LED",
                day.atTime(15, 0), day.atTime(17, 0),
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionOutboundToArrived(arrivedId, gate.getGateId(), day.atTime(14, 45), day.atTime(15, 5), day.atTime(17, 5));

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", scheduledId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualDeparture\":\"%s\"}".formatted(day.atTime(9, 5))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", departedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualDeparture\":\"%s\"}".formatted(day.atTime(12, 10))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", arrivedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", arrivedId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualDeparture":"%s","actualArrival":"%s"}
                                """.formatted(day.atTime(17, 0), day.atTime(15, 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Фактическое время прилёта не может быть раньше фактического времени вылета"));
    }

    @Test
    void correctActualTimes_forbiddenForReadOnly() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(27);
        int flightId = createFlightForScheduleRoute(
                "RO" + UNIQUE.incrementAndGet(), "SVO", "LED",
                day.atTime(7, 0), day.atTime(9, 0),
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionOutboundToArrived(flightId, gate.getGateId(), day.atTime(6, 45), day.atTime(7, 5), day.atTime(9, 5));

        String readerToken = login("reader", "reader123", "MOBILE");
        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", flightId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualArrival\":\"%s\"}".formatted(day.atTime(9, 10))))
                .andExpect(status().isForbidden());
    }

    @Test
    void readOnlyJwtCannotMutateFlightsOrSchedules() throws Exception {
        String readerToken = login("reader", "reader123", "MOBILE");
        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":1,\"operationDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
        int airlineId = airlineRepository.findByIataCode("SU").orElseThrow().getAirlineId();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(
                                "XX", "SVO", "LED",
                                LocalDate.of(2099, 1, 1).atTime(10, 0),
                                LocalDate.of(2099, 1, 1).atTime(12, 0),
                                airlineId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotDeleteFlightInDepartedOrArrivedStatus() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(20);
        int flightId = createFlightForSchedule("DEL" + UNIQUE.incrementAndGet(),
                day.atTime(8, 0), day.atTime(10, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), day.atTime(7, 45), day.atTime(10, 15))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DEPARTED","actualDeparture":"%s"}
                                """.formatted(day.atTime(8, 5))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isConflict());
        assertThat(flightRepository.findById(flightId)).isPresent();
    }

    @Test
    void cannotMutateResourcesWhenFlightCancelled() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(22);
        int flightId = createFlightForSchedule("CN" + UNIQUE.incrementAndGet(),
                day.atTime(9, 0), day.atTime(11, 0), airline.getAirlineId(), null);

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        int otherSlotId = scheduleRepository.findAll().stream()
                .filter(s -> "DP-205".equals(s.getFlightNumber()))
                .findFirst()
                .orElseThrow()
                .getSlots().getFirst().getSlotId();

        mockMvc.perform(put("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":%d,"operationDate":"%s"}
                                """.formatted(otherSlotId, day)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotTransitionToDepartedWithoutGateAircraftOrActualTime() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(23);
        int flightId = createFlightForSchedule("ND" + UNIQUE.incrementAndGet(),
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId(), null);

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEPARTED\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void schedulerDoesNotPersistActualDepartureWhenTransitionRejected() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "SK" + UNIQUE.incrementAndGet(),
                "SVO", "LED",
                LocalDateTime.now().minusMinutes(2),
                LocalDateTime.now().plusHours(1));
        Flight flight = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(reloaded.getActualDeparture()).isNull();
    }

    @Test
    void schedulerAutoArrival_completesDepartedOutboundSvoToLed() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "OUT" + UNIQUE.incrementAndGet(),
                "SVO", "LED",
                LocalDateTime.now().minusHours(3),
                LocalDateTime.now().minusMinutes(5));
        Flight flight = ScheduleTestFixtures.saveFlight(
                flightRepository, saved, FlightStatus.DEPARTED);
        flight.setActualDeparture(LocalDateTime.now().minusHours(2));
        flight.setAircraftType(aircraftType);
        flight = flightRepository.save(flight);

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.ARRIVED);
        assertThat(reloaded.getActualArrival()).isEqualTo(
                reloaded.getActualDeparture().plus(
                        java.time.Duration.between(reloaded.getScheduledDeparture(), reloaded.getScheduledArrival())));
    }

    @Test
    void schedulerInboundAutoDeparture_skippedWithoutAircraft() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "INNA" + UNIQUE.incrementAndGet(),
                "LED", "SVO",
                LocalDateTime.now().minusMinutes(15),
                LocalDateTime.now().plusHours(2));
        Flight flight = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.DELAYED);
        assertThat(reloaded.getActualDeparture()).isNull();
        var warnings = delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flight.getFlightId());
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getReason()).isEqualTo(FlightAutoStatusService.AUTO_INBOUND_MISSED_DEPARTURE_REASON);
    }

    @Test
    void schedulerAutoDeparture_completesScheduledInboundLedToSvo() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "IN" + UNIQUE.incrementAndGet(),
                "LED", "SVO",
                LocalDateTime.now().minusMinutes(30),
                LocalDateTime.now().plusHours(1));
        Flight flight = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);
        flight.setAircraftType(aircraftType);
        flight = flightRepository.save(flight);

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(reloaded.getActualDeparture()).isEqualTo(reloaded.getScheduledDeparture());
    }

    @Test
    void schedulerInboundDelayNoGate_afterGracePeriod() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "FRC" + UNIQUE.incrementAndGet(),
                "LED", "SVO",
                LocalDateTime.now().minusHours(4),
                LocalDateTime.now().minusMinutes(35));
        Flight flight = ScheduleTestFixtures.saveFlight(
                flightRepository, saved, FlightStatus.DEPARTED);
        flight.setActualDeparture(flight.getScheduledDeparture());
        flight.setAircraftType(aircraftType);
        flight = flightRepository.save(flight);

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.DELAYED);
        var warnings = delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flight.getFlightId());
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getReason()).isEqualTo(FlightAutoStatusService.AUTO_INBOUND_GATE_DELAY_REASON);
    }

    @Test
    void schedulerAutoDelay_createsWarningOnce() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDateTime scheduledDeparture = LocalDateTime.now().minusMinutes(10);
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "DL" + UNIQUE.incrementAndGet(),
                "SVO", "LED", scheduledDeparture, LocalDateTime.now().plusHours(2));
        Flight flight = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.SCHEDULED);

        flightStatusScheduler.updateFlightStatuses();

        Flight afterFirst = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(afterFirst.getStatus()).isEqualTo(FlightStatus.DELAYED);
        var warnings = delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flight.getFlightId());
        assertThat(warnings).hasSize(1);
        assertThat(warnings.getFirst().getDelayMinutes()).isGreaterThanOrEqualTo(5);
        assertThat(warnings.getFirst().getReason()).isEqualTo(FlightAutoStatusService.AUTO_DELAY_REASON);

        flightStatusScheduler.updateFlightStatuses();

        assertThat(delayWarningRepository.findByFlight_FlightIdOrderByCreatedAtDesc(flight.getFlightId())).hasSize(1);
        assertThat(flightRepository.findById(flight.getFlightId()).orElseThrow().getStatus())
                .isEqualTo(FlightStatus.DELAYED);
    }

    @Test
    void scheduleUpdate_rejectsInvalidInterval() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(40);
        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson(
                "IV" + UNIQUE.incrementAndGet(), "SVO", "LED",
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();

        MvcResult invalidInterval = mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJsonWithEqualSlotTimes(
                                "IV" + UNIQUE.incrementAndGet(), "SVO", "LED",
                                day.atTime(14, 0), airline.getAirlineId())))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(invalidInterval.getResponse().getContentAsString()).contains("совпад");
    }

    @Test
    void scheduleUpdate_rejectsTimeChangeWhenFlightDeparted() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDateTime dep = LocalDateTime.now().minusHours(2);
        LocalDateTime arr = LocalDateTime.now().plusHours(1);
        var saved = ScheduleTestFixtures.saveWeeklySchedule(
                scheduleRepository, airline, "LK" + UNIQUE.incrementAndGet(),
                "SVO", "LED", dep, arr);
        Schedule schedule = saved.schedule();
        Flight departed = ScheduleTestFixtures.saveFlight(flightRepository, saved, FlightStatus.DEPARTED);
        departed.setActualDeparture(dep.plusMinutes(5));
        flightRepository.save(departed);

        MvcResult locked = mockMvc.perform(put("/api/v1/schedules/{id}", schedule.getScheduleId())
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(
                                schedule.getFlightNumber(), "SVO", "LED",
                                dep.plusHours(1), arr.plusHours(1), airline.getAirlineId())))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(locked.getResponse().getContentAsString()).contains("DEPARTED");
    }

    @Test
    void cannotCreateScheduleOutsideHomeAirport() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(
                                "XX" + UNIQUE.incrementAndGet(), "LED", "AER",
                                LocalDateTime.now().plusDays(30).withHour(10),
                                LocalDateTime.now().plusDays(30).withHour(12),
                                airline.getAirlineId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotCreateFlightForScheduleOutsideHomeAirport() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson(
                "XX" + UNIQUE.incrementAndGet(), "LED", "AER",
                LocalDateTime.now().plusDays(30).withHour(10),
                LocalDateTime.now().plusDays(30).withHour(12),
                airline.getAirlineId()));

        int slotId = schedule.get("slots").get(0).get("slotId").asInt();
        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":%d,"operationDate":"%s"}
                                """.formatted(slotId, LocalDate.now().plusDays(30))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void gateAssignmentRejectedWhenIntervalFullyInPast() throws Exception {
        AircraftType aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate gate = gateRepository.findByGateNumber("101").orElseThrow();
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(21);

        int flightId = createFlightForSchedule("PZ" + UNIQUE.incrementAndGet(),
                day.atTime(10, 0), day.atTime(12, 0), airline.getAirlineId(), aircraft.getAircraftTypeId());

        LocalDateTime pastEnd = LocalDateTime.now(ZoneOffset.UTC).minusHours(1);
        LocalDateTime pastStart = pastEnd.minusHours(2);

        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gate.getGateId(), pastStart, pastEnd)))
                .andExpect(status().isConflict());
    }

    // ═══ UC8: Предупреждения о задержке + WebSocket ═══

    @Test
    void useCase8_delayWarningsAreCreatedListedAndPublished() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(13);
        int flightId = createFlightForSchedule("DL" + UNIQUE.incrementAndGet(),
                day.atTime(15, 0), day.atTime(17, 0), airline.getAirlineId(), null);

        mockMvc.perform(post("/api/v1/flights/{id}/delay-warnings", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"delayMinutes":10,"reason":"Too early"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELAYED\"}"))
                .andExpect(status().isOk());

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
        return createFlightForScheduleRoute(
                flightNumber, "SVO", "LED", departure, arrival, airlineId, aircraftTypeId);
    }

    private int createFlightForScheduleRoute(
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

    private void transitionOutboundToDeparted(
            int flightId, int gateId, LocalDateTime gateFrom, LocalDateTime actualDeparture) throws Exception {
        mockMvc.perform(post("/api/v1/flights/{id}/gate-assignment", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gateId":%d,"assignedFrom":"%s","assignedTo":"%s"}
                                """.formatted(gateId, gateFrom, actualDeparture.plusHours(2))))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DEPARTED","actualDeparture":"%s"}
                                """.formatted(actualDeparture)))
                .andExpect(status().isOk());
    }

    private void transitionOutboundToArrived(
            int flightId,
            int gateId,
            LocalDateTime gateFrom,
            LocalDateTime actualDeparture,
            LocalDateTime actualArrival
    ) throws Exception {
        transitionOutboundToDeparted(flightId, gateId, gateFrom, actualDeparture);

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ARRIVED","actualArrival":"%s"}
                                """.formatted(actualArrival)))
                .andExpect(status().isOk());
    }

    private void transitionInboundToArrived(
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

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DEPARTED","actualDeparture":"%s"}
                                """.formatted(actualDeparture)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/flights/{id}/status", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ARRIVED","actualArrival":"%s"}
                                """.formatted(actualArrival)))
                .andExpect(status().isOk());
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

    @SafeVarargs
    private final List<Integer> runConcurrently(Callable<Integer>... tasks)
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

    private int postGateAssignmentStatus(Integer flightId, Integer gateId, LocalDateTime from, LocalDateTime to)
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

    private String login(String username, String password) throws Exception {
        return login(username, password, "DISPATCHER");
    }

    private String login(String username, String password, String client) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s","client":"%s"}
                                """.formatted(username, password, client)))
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

    private static String scheduleJsonWithEqualSlotTimes(
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

    // ═══ Periodicity: шаблон ↔ экземпляры ═══

    @Test
    void scheduleCreate_rejectsDowOutsideShortEffectivePeriod() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber":"PDOW",
                                  "originAirport":"SVO",
                                  "destinationAirport":"LED",
                                  "effectiveFrom":"2026-06-01",
                                  "effectiveTo":"2026-06-03",
                                  "periodicityType":"WEEKLY",
                                  "periodicityStep":1,
                                  "airlineId":%d,
                                  "slots":[{"dayOfWeek":7,"departureTime":"10:00","arrivalTime":"12:00"}]
                                }
                                """.formatted(airline.getAirlineId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Слот для дня недели 7 не попадает в короткий период действия расписания (с 2026-06-01 по 2026-06-03)"));
    }

    @Test
    void generate_returnsZeroWhenBiweeklyCycleInactive() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        JsonNode schedule = postAuthorized("/api/v1/schedules", """
                {
                  "flightNumber":"BWK",
                  "originAirport":"SVO",
                  "destinationAirport":"LED",
                  "effectiveFrom":"2026-06-01",
                  "effectiveTo":"2026-06-10",
                  "periodicityType":"WEEKLY",
                  "periodicityStep":2,
                  "airlineId":%d,
                  "slots":[{"dayOfWeek":7,"departureTime":"10:00","arrivalTime":"12:00"}]
                }
                """.formatted(airline.getAirlineId()));

        MvcResult result = mockMvc.perform(post("/api/v1/flights/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDate":"2026-06-08","toDate":"2026-06-13","scheduleId":%d}
                                """.formatted(schedule.get("scheduleId").asInt())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("created").asInt()).isZero();
        assertThat(body.get("skipped").asInt()).isZero();
    }

    @Test
    void generate_intervalEveryThreeDays() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        JsonNode schedule = postAuthorized("/api/v1/schedules", """
                {
                  "flightNumber":"INT3",
                  "originAirport":"SVO",
                  "destinationAirport":"LED",
                  "effectiveFrom":"2026-05-01",
                  "periodicityType":"INTERVAL",
                  "periodicityStep":3,
                  "airlineId":%d,
                  "slots":[{"departureTime":"14:15","arrivalTime":"15:45"}]
                }
                """.formatted(airline.getAirlineId()));

        MvcResult result = mockMvc.perform(post("/api/v1/flights/generate")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromDate":"2026-05-01","toDate":"2026-05-10","scheduleId":%d}
                                """.formatted(schedule.get("scheduleId").asInt())))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("created").asInt()).isEqualTo(4);
    }

    @Test
    void manualFlightCreate_rejectsInactivePeriodicityDate() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        JsonNode schedule = postAuthorized("/api/v1/schedules", """
                {
                  "flightNumber":"MAN",
                  "originAirport":"SVO",
                  "destinationAirport":"LED",
                  "effectiveFrom":"2026-06-01",
                  "effectiveTo":"2026-06-10",
                  "periodicityType":"WEEKLY",
                  "periodicityStep":2,
                  "airlineId":%d,
                  "slots":[{"dayOfWeek":7,"departureTime":"10:00","arrivalTime":"12:00"}]
                }
                """.formatted(airline.getAirlineId()));
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();

        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"slotId":%d,"operationDate":"2026-06-14"}
                                """.formatted(slotId)))
                .andExpect(status().isBadRequest());
    }

    private static String uniqueAirlineCode() {
        int value = UNIQUE.incrementAndGet() % 36;
        return "Q" + Character.toUpperCase(Character.forDigit(value, 36));
    }

    private static String uniqueAircraftCode() {
        return "Q" + Integer.toString(1000 + UNIQUE.incrementAndGet()).substring(1);
    }
}
