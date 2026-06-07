package ru.airport.audit;

import org.springframework.stereotype.Component;
import ru.airport.dto.ScheduleRs;
import ru.airport.model.OperationalEventCategory;
import ru.airport.websocket.payload.OperationalEventPush;

import java.util.Optional;

import static ru.airport.audit.OperationalEventSupport.firstIntegerArg;
import static ru.airport.audit.OperationalEventSupport.trimDetails;

/** Сообщения operational feed для шаблонов расписания. */
@Component
public class ScheduleOperationalMessages {

    public Optional<OperationalEventPush> map(
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
}
