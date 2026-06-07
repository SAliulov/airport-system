package ru.airport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import ru.airport.testsupport.AbstractAirportIT;
import ru.airport.testsupport.TestAuthHelper;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth и RBAC integration-тесты. */
@EnabledIf(value = "ru.airport.testsupport.DockerConditions#isDockerAvailable",
        disabledReason = "Нужен Docker для PostgreSQL Testcontainers")
class SecurityUseCasesIT extends AbstractAirportIT {

    @Test
    void login_withCyrillicUsername_succeeds() throws Exception {
        String token = login("Диспетчер_Женя1987", "eugene_egov_is_cool_dispatcher_kotcheshir777", "DISPATCHER");
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(token)))
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
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("READ_ONLY"));
    }

    @Test
    void readOnlyJwtCannotMutateFlightsOrSchedules() throws Exception {
        String readerToken = login("reader", "reader123", "MOBILE");
        mockMvc.perform(post("/api/v1/flights")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(readerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"slotId\":1,\"operationDate\":\"2026-01-01\"}"))
                .andExpect(status().isForbidden());
        int airlineId = airlineRepository.findByIataCode("SU").orElseThrow().getAirlineId();
        mockMvc.perform(post("/api/v1/schedules")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(readerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleJson(
                                "XX", "SVO", "LED",
                                LocalDate.of(2099, 1, 1).atTime(10, 0),
                                LocalDate.of(2099, 1, 1).atTime(12, 0),
                                airlineId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void useCase6_exportForbiddenForReadOnlyUser() throws Exception {
        String readerToken = login("reader", "reader123", "MOBILE");
        LocalDate day = LocalDate.now().plusDays(11);

        mockMvc.perform(get("/api/v1/schedules/export/pdf")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(readerToken))
                        .param("date", day.toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/schedules/export/excel")
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(readerToken))
                        .param("date", day.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctActualTimes_forbiddenForReadOnly() throws Exception {
        var airline = airlineRepository.findByIataCode("SU").orElseThrow();
        var aircraft = aircraftTypeRepository.findByIcaoCode("A320").orElseThrow();
        var gate = gateRepository.findByGateNumber("101").orElseThrow();
        LocalDate day = LocalDate.now().plusDays(27);
        int flightId = createFlightForScheduleRoute(
                "RO" + UNIQUE.incrementAndGet(), "SVO", "LED",
                day.atTime(7, 0), day.atTime(9, 0),
                airline.getAirlineId(), aircraft.getAircraftTypeId());
        transitionOutboundToArrived(flightId, gate.getGateId(), day.atTime(6, 45), day.atTime(7, 5), day.atTime(9, 5));

        String readerToken = login("reader", "reader123", "MOBILE");
        mockMvc.perform(put("/api/v1/flights/{id}/actual-times", flightId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuthHelper.bearer(readerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualArrival\":\"%s\"}".formatted(day.atTime(9, 10))))
                .andExpect(status().isForbidden());
    }
}
