package ru.airport.audit;

import org.springframework.stereotype.Component;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightGenerateRq;
import ru.airport.dto.FlightGenerateRs;
import ru.airport.dto.FlightRs;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.model.OperationalEventCategory;
import ru.airport.websocket.payload.OperationalEventPush;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static ru.airport.audit.OperationalEventSupport.FlightContext;
import static ru.airport.audit.OperationalEventSupport.firstIntegerArg;
import static ru.airport.audit.OperationalEventSupport.statusLabel;
import static ru.airport.audit.OperationalEventSupport.trimDetails;

/** Сообщения operational feed для рейсов. */
@Component
public class FlightOperationalMessages {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Optional<OperationalEventPush> map(
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
}
