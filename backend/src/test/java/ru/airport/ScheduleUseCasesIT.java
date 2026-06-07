package ru.airport;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import ru.airport.model.Airline;
import ru.airport.testsupport.AbstractAirportIT;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Расписание, периодичность и генерация рейсов. */
@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class ScheduleUseCasesIT extends AbstractAirportIT {

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
                        "Нельзя деактивировать шаблон: есть рейсы в статусе SCHEDULED, DEPARTED или DELAYED."));
    }

    @Test
    void scheduleUpdate_allowsDeactivationWhenOnlyHistoricalFlights() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        LocalDate day = LocalDate.of(2026, 6, 4);

        JsonNode schedule = postAuthorized("/api/v1/schedules", scheduleJson("UT779", "SVO", "LED",
                day.atTime(8, 0), day.atTime(10, 0), airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();
        int slotId = schedule.get("slots").get(0).get("slotId").asInt();

        JsonNode flight = postAuthorized("/api/v1/flights", """
                {"slotId":%d,"operationDate":"%s"}
                """.formatted(slotId, day));
        int flightId = flight.get("flightId").asInt();

        mockMvc.perform(put("/api/v1/flights/%d/status".formatted(flightId))
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/schedules/{id}", scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flightNumber":"UT779",
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void scheduleExpiry_deactivatesTemplateAfterEffectiveTo() throws Exception {
        Airline airline = airlineRepository.findByIataCode("SU").orElseThrow();
        JsonNode schedule = postAuthorized("/api/v1/schedules", """
                {
                  "flightNumber":"EXP1",
                  "originAirport":"SVO",
                  "destinationAirport":"LED",
                  "effectiveFrom":"2026-05-01",
                  "effectiveTo":"2026-05-01",
                  "isActive":true,
                  "periodicityType":"INTERVAL",
                  "periodicityStep":1,
                  "airlineId":%d,
                  "slots":[{"departureTime":"10:00","arrivalTime":"12:00"}]
                }
                """.formatted(airline.getAirlineId()));
        int scheduleId = schedule.get("scheduleId").asInt();

        flightStatusScheduler.updateFlightStatuses();

        mockMvc.perform(get("/api/v1/schedules/{id}", scheduleId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

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
}
