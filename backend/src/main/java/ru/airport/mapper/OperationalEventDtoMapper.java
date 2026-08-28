package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.dto.OperationalEventRs;
import ru.airport.model.OperationalEvent;

import java.time.format.DateTimeFormatter;

@Component
public class OperationalEventDtoMapper {

    public OperationalEventRs toOperationalEventRs(OperationalEvent e) {
        return OperationalEventRs.builder()
                .operationalEventId(e.getOperationalEventId())
                .timestamp(e.getEventTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .user(e.getUsername())
                .category(e.getCategory())
                .message(e.getMessage())
                .details(e.getDetails())
                .flightId(e.getFlightId())
                .flightNumber(e.getFlightNumber())
                .build();
    }
}
