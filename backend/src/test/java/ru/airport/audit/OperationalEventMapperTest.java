package ru.airport.audit;

import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.ScheduleRs;
import ru.airport.model.FlightStatus;
import ru.airport.model.OperationalEventCategory;
import ru.airport.model.PeriodicityType;
import ru.airport.service.FlightService;
import ru.airport.service.ScheduleService;
import ru.airport.websocket.payload.OperationalEventPush;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalEventMapperTest {

    private OperationalEventMapper mapper;
    private UsernamePasswordAuthenticationToken dispatcherAuth;

    @BeforeEach
    void setUp() {
        AirportProperties properties = new AirportProperties();
        properties.setHomeIata("SVO");
        properties.setTimezone("Europe/Moscow");
        mapper = new OperationalEventMapper(new AirportClock(properties));
        dispatcherAuth = new UsernamePasswordAuthenticationToken(
                "dispatcher",
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_DISPATCHER")));
    }

    @Test
    void mapsFlightStatusUpdate_forDispatcher() throws Exception {
        FlightRs flight = FlightRs.builder()
                .flightId(10)
                .status(FlightStatus.DEPARTED)
                .schedule(ScheduleRs.builder().flightNumber("SU-1002").build())
                .build();
        MethodSignature sig = methodSignature(FlightService.class, "updateStatus", Integer.class, FlightStatusUpdateRq.class);

        Optional<OperationalEventPush> event = mapper.tryMap(
                sig,
                new Object[]{10, FlightStatusUpdateRq.builder().status(FlightStatus.DEPARTED).build()},
                flight,
                "dispatcher",
                dispatcherAuth,
                " | flightId=10, newStatus=DEPARTED");

        assertThat(event).isPresent();
        assertThat(event.get().category()).isEqualTo(OperationalEventCategory.STATUS);
        assertThat(event.get().message()).contains("SU-1002").contains("Вылетел");
        assertThat(event.get().message()).doesNotContain("updateStatus");
    }

    @Test
    void skipsAnonymousUser() throws Exception {
        MethodSignature sig = methodSignature(ScheduleService.class, "create", ru.airport.dto.ScheduleRq.class);
        ScheduleRs schedule = ScheduleRs.builder()
                .flightNumber("DP-205")
                .periodicityType(PeriodicityType.INTERVAL)
                .periodicityStep(3)
                .build();

        Optional<OperationalEventPush> event = mapper.tryMap(sig, new Object[]{}, schedule, "anonymous", null, "");

        assertThat(event).isEmpty();
    }

    @Test
    void mapsScheduleCreate_withPeriodicity() throws Exception {
        MethodSignature sig = methodSignature(ScheduleService.class, "create", ru.airport.dto.ScheduleRq.class);
        ScheduleRs schedule = ScheduleRs.builder()
                .flightNumber("SU-1103")
                .periodicityType(PeriodicityType.WEEKLY)
                .periodicityStep(2)
                .build();

        Optional<OperationalEventPush> event = mapper.tryMap(sig, new Object[]{}, schedule, "dispatcher", dispatcherAuth, "");

        assertThat(event).isPresent();
        assertThat(event.get().category()).isEqualTo(OperationalEventCategory.SCHEDULE);
        assertThat(event.get().message()).contains("SU-1103").contains("WEEKLY");
    }

    @Test
    void mapsAircraftAssignment_withIcaoCode() throws Exception {
        MethodSignature sig = methodSignature(FlightService.class, "assignAircraft", Integer.class, ru.airport.dto.FlightAircraftAssignmentRq.class);
        FlightRs flight = FlightRs.builder()
                .flightId(5)
                .schedule(ScheduleRs.builder().flightNumber("SU-1002").build())
                .aircraftType(AircraftTypeRs.builder().icaoCode("A320").build())
                .build();

        Optional<OperationalEventPush> event = mapper.tryMap(sig, new Object[]{5, null}, flight, "dispatcher", dispatcherAuth, "");

        assertThat(event).isPresent();
        assertThat(event.get().category()).isEqualTo(OperationalEventCategory.AIRCRAFT);
        assertThat(event.get().message()).contains("A320");
    }

    private static MethodSignature methodSignature(Class<?> type, String name, Class<?>... paramTypes)
            throws NoSuchMethodException {
        Method method = type.getMethod(name, paramTypes);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getDeclaringType()).thenReturn(type);
        when(sig.getMethod()).thenReturn(method);
        return sig;
    }
}
