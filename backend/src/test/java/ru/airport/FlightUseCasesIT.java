package ru.airport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.service.FlightAutoStatusService;
import ru.airport.testsupport.AbstractAirportIT;
import ru.airport.testsupport.ScheduleTestFixtures;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class FlightUseCasesIT extends AbstractAirportIT {

    @Autowired
    private FlightAutoStatusService flightAutoStatusService;


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
}
