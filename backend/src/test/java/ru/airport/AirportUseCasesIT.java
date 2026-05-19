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
import ru.airport.testsupport.DockerConditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

        JsonNode alternateSchedule = postAuthorized("/api/v1/schedules", scheduleJson("UT202", "AER", "SVO",
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

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("origin", "AER")
                        .param("destination", "SVO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).exists());

        mockMvc.perform(get("/api/v1/flights/filter")
                        .param("origin", "SVO")
                        .param("destination", "AER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.flightId == %d)]".formatted(flightId)).isEmpty());

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
        AircraftType a320 = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Gate departureGate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDateTime depFrom = LocalDateTime.now().minusHours(1);
        LocalDateTime depTo = LocalDateTime.now().plusHours(2);
        Flight departureFlight = Flight.builder()
                .schedule(departureSchedule)
                .status(FlightStatus.SCHEDULED)
                .aircraftType(a320)
                .build();
        departureFlight.getGateAssignments().add(GateAssignment.builder()
                .flight(departureFlight)
                .gate(departureGate)
                .assignedFrom(depFrom)
                .assignedTo(depTo)
                .build());
        departureFlight = flightRepository.save(departureFlight);

        Schedule arrivalSchedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("ARR" + UNIQUE.incrementAndGet())
                .originAirport("LED")
                .destinationAirport("SVO")
                .scheduledDeparture(LocalDateTime.now().minusHours(3))
                .scheduledArrival(LocalDateTime.now().minusMinutes(10))
                .airline(airline)
                .build());
        Gate arrivalGate = gateRepository.findByGateNumber("126").orElseThrow();
        Flight arrivalFlight = Flight.builder()
                .schedule(arrivalSchedule)
                .status(FlightStatus.SCHEDULED)
                .aircraftType(a320)
                .build();
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
        assertThat(updatedArrival.getActualDeparture()).isEqualTo(arrivalSchedule.getScheduledDeparture());
        verify(messagingTemplate, atLeastOnce())
                .convertAndSend(ArgumentMatchers.eq("/topic/flights"), ArgumentMatchers.any(Object.class));
    }

    @Test
    void useCase4_gateAssignment_detectsConflicts_andUseCase7_timelineReturnsSegments() throws Exception {
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

        mockMvc.perform(get("/api/v1/gates/timeline").param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightId").value(flightOneId))
                .andExpect(jsonPath("$[0].gateNumber").value("101"));
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
    void readOnlyJwtCannotMutateFlightsOrSchedules() throws Exception {
        String readerToken = login("reader", "reader123");
        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduleId\":1}"))
                .andExpect(status().isForbidden());
        int airlineId = airlineRepository.findByIataCode("SU").orElseThrow().getAirlineId();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"flightNumber":"XX","originAirport":"SVO","destinationAirport":"LED",
                                "scheduledDeparture":"2099-01-01T10:00:00","scheduledArrival":"2099-01-01T12:00:00",
                                "airlineId":%d}
                                """.formatted(airlineId)))
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

        int otherScheduleId = scheduleRepository.findAll().stream()
                .filter(s -> "DP-206".equals(s.getFlightNumber()))
                .findFirst()
                .orElseThrow()
                .getScheduleId();

        mockMvc.perform(put("/api/v1/flights/{id}", flightId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":%d}
                                """.formatted(otherScheduleId)))
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
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("SK" + UNIQUE.incrementAndGet())
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(LocalDateTime.now().minusMinutes(2))
                .scheduledArrival(LocalDateTime.now().plusHours(1))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build());

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(reloaded.getActualDeparture()).isNull();
    }

    @Test
    void schedulerAutoArrival_completesDepartedOutboundSvoToLed() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("OUT" + UNIQUE.incrementAndGet())
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(LocalDateTime.now().minusHours(3))
                .scheduledArrival(LocalDateTime.now().minusMinutes(5))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.DEPARTED)
                .actualDeparture(LocalDateTime.now().minusHours(2))
                .aircraftType(aircraftType)
                .build());

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.ARRIVED);
        assertThat(reloaded.getActualArrival()).isEqualTo(
                reloaded.getActualDeparture().plus(
                        java.time.Duration.between(schedule.getScheduledDeparture(), schedule.getScheduledArrival())));
    }

    @Test
    void schedulerInboundAutoDeparture_skippedWithoutAircraft() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("INNA" + UNIQUE.incrementAndGet())
                .originAirport("LED")
                .destinationAirport("SVO")
                .scheduledDeparture(LocalDateTime.now().minusMinutes(15))
                .scheduledArrival(LocalDateTime.now().plusHours(2))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build());

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.SCHEDULED);
        assertThat(reloaded.getActualDeparture()).isNull();
    }

    @Test
    void schedulerAutoDeparture_completesScheduledInboundLedToSvo() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("IN" + UNIQUE.incrementAndGet())
                .originAirport("LED")
                .destinationAirport("SVO")
                .scheduledDeparture(LocalDateTime.now().minusMinutes(30))
                .scheduledArrival(LocalDateTime.now().plusHours(1))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .aircraftType(aircraftType)
                .build());

        flightStatusScheduler.updateFlightStatuses();

        Flight reloaded = flightRepository.findById(flight.getFlightId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FlightStatus.DEPARTED);
        assertThat(reloaded.getActualDeparture()).isEqualTo(schedule.getScheduledDeparture());
    }

    @Test
    void schedulerInboundDelayNoGate_afterGracePeriod() {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        AircraftType aircraftType = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("FRC" + UNIQUE.incrementAndGet())
                .originAirport("LED")
                .destinationAirport("SVO")
                .scheduledDeparture(LocalDateTime.now().minusHours(4))
                .scheduledArrival(LocalDateTime.now().minusMinutes(35))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.DEPARTED)
                .actualDeparture(schedule.getScheduledDeparture())
                .aircraftType(aircraftType)
                .build());

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
        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .flightNumber("DL" + UNIQUE.incrementAndGet())
                .originAirport("SVO")
                .destinationAirport("LED")
                .scheduledDeparture(scheduledDeparture)
                .scheduledArrival(LocalDateTime.now().plusHours(2))
                .airline(airline)
                .build());
        Flight flight = flightRepository.save(Flight.builder()
                .schedule(schedule)
                .status(FlightStatus.SCHEDULED)
                .build());

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

        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scheduleId":%d}
                                """.formatted(schedule.get("scheduleId").asInt())))
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
