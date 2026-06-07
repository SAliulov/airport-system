package ru.airport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;
import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.Schedule;
import ru.airport.service.FlightAutoStatusService;
import ru.airport.testsupport.AbstractAirportIT;
import ru.airport.testsupport.ScheduleTestFixtures;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

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

@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class AirportUseCasesIT extends AbstractAirportIT {

    @Autowired
    private FlightAutoStatusService flightAutoStatusService;

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

    // ═══ Дополнительно: инварианты статусов и scheduler ═══

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

}
