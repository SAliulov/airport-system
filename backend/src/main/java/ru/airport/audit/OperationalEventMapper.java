package ru.airport.audit;

import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import ru.airport.config.AirportClock;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightGenerateRq;
import ru.airport.dto.FlightGenerateRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.ScheduleRs;
import ru.airport.model.FlightStatus;
import ru.airport.model.OperationalEventCategory;
import ru.airport.service.FlightService;
import ru.airport.service.ScheduleService;
import ru.airport.websocket.payload.OperationalEventPush;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Перевод успешных мутаций диспетчера в человекочитаемые события операционной ленты.
 */
@Component
public class OperationalEventMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final AirportClock airportClock;

    public OperationalEventMapper(AirportClock airportClock) {
        this.airportClock = airportClock;
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
            return mapFlightService(methodName, args, result, username, timestamp, details);
        }
        if (ScheduleService.class.isAssignableFrom(serviceClass)) {
            return mapScheduleService(methodName, args, result, username, timestamp, details);
        }
        return Optional.empty();
    }

    private Optional<OperationalEventPush> mapFlightService(
            String methodName,
            Object[] args,
            Object result,
            String username,
            String timestamp,
            String details) {
        return switch (methodName) {
            case "updateStatus" -> mapStatusUpdate(result, username, timestamp, details);
            case "assignGate" -> mapGateAssignment(args, result, username, timestamp, details);
            case "assignAircraft" -> mapAircraftAssignment(result, username, timestamp, details);
            case "addDelayWarning" -> mapDelayWarning(args, result, username, timestamp, details);
            case "create" -> mapFlightCreate(result, username, timestamp, details);
            case "generate" -> mapFlightGenerate(args, result, username, timestamp, details);
            case "delete" -> mapFlightDelete(args, username, timestamp, details);
            case "correctActualTimes" -> mapCorrectActualTimes(result, username, timestamp, details);
            default -> Optional.empty();
        };
    }

    private Optional<OperationalEventPush> mapScheduleService(
            String methodName,
            Object[] args,
            Object result,
            String username,
            String timestamp,
            String details) {
        return switch (methodName) {
            case "create" -> mapScheduleMutation(result, username, timestamp, details, "Создан шаблон расписания");
            case "update" -> mapScheduleMutation(result, username, timestamp, details, "Обновлён шаблон расписания");
            case "delete" -> mapScheduleDelete(args, username, timestamp, details);
            default -> Optional.empty();
        };
    }

    private Optional<OperationalEventPush> mapStatusUpdate(
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof FlightRs flight)) {
            return Optional.empty();
        }
        String fn = flightNumber(flight);
        String statusRu = statusLabel(flight.getStatus());
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.STATUS,
                "Рейс %s: статус изменён на %s".formatted(fn, statusRu),
                trimDetails(details),
                flight.getFlightId(),
                fn));
    }

    private Optional<OperationalEventPush> mapGateAssignment(
            Object[] args,
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof GateAssignmentRs assignment)) {
            return Optional.empty();
        }
        FlightContext ctx = flightContextFromArgs(args);
        String gateNum = assignment.getGate() != null ? assignment.getGate().getGateNumber() : "?";
        String interval = "";
        if (assignment.getAssignedFrom() != null && assignment.getAssignedTo() != null) {
            interval = " (%s–%s)".formatted(
                    assignment.getAssignedFrom().format(TIME_FMT),
                    assignment.getAssignedTo().format(TIME_FMT));
        }
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.GATE,
                "Рейсу %s назначен гейт %s%s".formatted(ctx.flightNumber(), gateNum, interval),
                trimDetails(details),
                ctx.flightId(),
                ctx.flightNumber()));
    }

    private Optional<OperationalEventPush> mapAircraftAssignment(
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof FlightRs flight)) {
            return Optional.empty();
        }
        String fn = flightNumber(flight);
        String icao = flight.getAircraftType() != null ? flight.getAircraftType().getIcaoCode() : "?";
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.AIRCRAFT,
                "Рейсу %s назначен тип ВС %s".formatted(fn, icao),
                trimDetails(details),
                flight.getFlightId(),
                fn));
    }

    private Optional<OperationalEventPush> mapDelayWarning(
            Object[] args,
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof DelayWarningRs warning)) {
            return Optional.empty();
        }
        FlightContext ctx = flightContextFromArgs(args);
        String reason = warning.getReason() != null && !warning.getReason().isBlank()
                ? " — " + warning.getReason()
                : "";
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.DELAY,
                "Задержка %s: %d мин%s".formatted(ctx.flightNumber(), warning.getDelayMinutes(), reason),
                trimDetails(details),
                ctx.flightId(),
                ctx.flightNumber()));
    }

    private Optional<OperationalEventPush> mapFlightCreate(
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof FlightRs flight)) {
            return Optional.empty();
        }
        String fn = flightNumber(flight);
        String date = flight.getOperationDate() != null
                ? flight.getOperationDate().format(DATE_FMT)
                : "?";
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.FLIGHT,
                "Создан рейс %s на %s".formatted(fn, date),
                trimDetails(details),
                flight.getFlightId(),
                fn));
    }

    private Optional<OperationalEventPush> mapFlightGenerate(
            Object[] args,
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof FlightGenerateRs gen)) {
            return Optional.empty();
        }
        String period = "?";
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof FlightGenerateRq rq) {
                    period = "%s–%s".formatted(
                            rq.getFromDate().format(DATE_FMT),
                            rq.getToDate().format(DATE_FMT));
                    break;
                }
            }
        }
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.FLIGHT,
                "Сгенерировано %d рейсов за период %s (пропущено: %d)".formatted(
                        gen.getCreated(), period, gen.getSkipped()),
                trimDetails(details),
                null,
                null));
    }

    private Optional<OperationalEventPush> mapFlightDelete(
            Object[] args,
            String username,
            String timestamp,
            String details) {
        Integer flightId = firstIntegerArg(args);
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.FLIGHT,
                "Удалён рейс #%s".formatted(flightId != null ? flightId : "?"),
                trimDetails(details),
                flightId,
                null));
    }

    private Optional<OperationalEventPush> mapCorrectActualTimes(
            Object result,
            String username,
            String timestamp,
            String details) {
        if (!(result instanceof FlightRs flight)) {
            return Optional.empty();
        }
        String fn = flightNumber(flight);
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.STATUS,
                "Скорректированы фактические времена рейса %s".formatted(fn),
                trimDetails(details),
                flight.getFlightId(),
                fn));
    }

    private Optional<OperationalEventPush> mapScheduleMutation(
            Object result,
            String username,
            String timestamp,
            String details,
            String action) {
        if (!(result instanceof ScheduleRs schedule)) {
            return Optional.empty();
        }
        String periodicity = schedule.getPeriodicityType() != null
                ? schedule.getPeriodicityType().name()
                : "?";
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.SCHEDULE,
                "%s %s (%s, шаг %d)".formatted(
                        action,
                        schedule.getFlightNumber(),
                        periodicity,
                        schedule.getPeriodicityStep() != null ? schedule.getPeriodicityStep() : 1),
                trimDetails(details),
                null,
                schedule.getFlightNumber()));
    }

    private Optional<OperationalEventPush> mapScheduleDelete(
            Object[] args,
            String username,
            String timestamp,
            String details) {
        Integer scheduleId = firstIntegerArg(args);
        return Optional.of(new OperationalEventPush(
                timestamp,
                username,
                OperationalEventCategory.SCHEDULE,
                "Удалён шаблон расписания #%s".formatted(scheduleId != null ? scheduleId : "?"),
                trimDetails(details),
                null,
                null));
    }

    private static boolean isDispatcher(Authentication authentication, String username) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if ("anonymous".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_DISPATCHER"::equals);
    }

    private static String flightNumber(FlightRs flight) {
        if (flight.getSchedule() != null && flight.getSchedule().getFlightNumber() != null) {
            return flight.getSchedule().getFlightNumber();
        }
        return "#" + flight.getFlightId();
    }

    private static FlightContext flightContextFromArgs(Object[] args) {
        Integer flightId = firstIntegerArg(args);
        return new FlightContext(flightId, flightId != null ? "#" + flightId : "?");
    }

    private static Integer firstIntegerArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Integer id) {
                return id;
            }
        }
        return null;
    }

    private static String trimDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String trimmed = details.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String statusLabel(FlightStatus status) {
        if (status == null) {
            return "?";
        }
        return switch (status) {
            case SCHEDULED -> "Запланирован";
            case DEPARTED -> "Вылетел";
            case ARRIVED -> "Прибыл";
            case DELAYED -> "Задержан";
            case CANCELLED -> "Отменён";
        };
    }

    private record FlightContext(Integer flightId, String flightNumber) {
    }
}
