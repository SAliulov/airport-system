package ru.airport.audit;

import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import ru.airport.config.AirportClock;
import ru.airport.service.FlightService;
import ru.airport.service.ScheduleService;
import ru.airport.websocket.payload.OperationalEventPush;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static ru.airport.audit.OperationalEventSupport.isDispatcher;

/**
 * Фасад: перевод успешных мутаций диспетчера в человекочитаемые события операционной ленты.
 */
@Component
public class OperationalEventMapper {

    private final AirportClock airportClock;
    private final FlightOperationalMessages flightMessages;
    private final ScheduleOperationalMessages scheduleMessages;

    public OperationalEventMapper(
            AirportClock airportClock,
            FlightOperationalMessages flightMessages,
            ScheduleOperationalMessages scheduleMessages) {
        this.airportClock = airportClock;
        this.flightMessages = flightMessages;
        this.scheduleMessages = scheduleMessages;
    }

    public Optional<OperationalEventPush> tryMap(
            MethodSignature methodSig,
            Object[] args,
            Object result,
            String username,
            Authentication authentication,
            String details) {
        if (!isDispatcher(authentication, username)) {
            return Optional.empty();
        }

        Class<?> serviceClass = methodSig.getDeclaringType();
        String methodName = methodSig.getMethod().getName();
        String timestamp = airportClock.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        if (FlightService.class.isAssignableFrom(serviceClass)) {
            return flightMessages.map(methodName, args, result, username, timestamp, details);
        }
        if (ScheduleService.class.isAssignableFrom(serviceClass)) {
            return scheduleMessages.map(methodName, args, result, username, timestamp, details);
        }
        return Optional.empty();
    }
}
