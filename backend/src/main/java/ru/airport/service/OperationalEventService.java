package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.dto.OperationalEventRs;
import ru.airport.dto.PageRs;
import ru.airport.mapper.OperationalEventDtoMapper;
import ru.airport.model.OperationalEvent;
import ru.airport.repository.OperationalEventRepository;
import ru.airport.websocket.payload.OperationalEventPush;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * История операционных событий (журнал действий диспетчера) — читает REST-эндпоинт
 * мобильного клиента; запись идёт из {@link ru.airport.websocket.RealtimeNotificationService}
 * рядом с публикацией того же события в /topic/operational-events.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationalEventService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MESSAGE_MAX_LENGTH = 500;

    private final OperationalEventRepository repository;
    private final OperationalEventDtoMapper mapper;

    public PageRs<OperationalEventRs> list(Integer page, Integer size) {
        int pageIndex = page != null && page >= 0 ? page : 0;
        int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Sort sort = Sort.by(Sort.Order.desc("eventTimestamp"), Sort.Order.desc("operationalEventId"));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        Page<OperationalEvent> result = repository.findAll(pageable);
        List<OperationalEventRs> content = result.getContent().stream()
                .map(mapper::toOperationalEventRs)
                .toList();
        return PageRs.of(content, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional
    public void persist(OperationalEventPush push) {
        OperationalEvent entity = OperationalEvent.builder()
                .eventTimestamp(LocalDateTime.parse(push.timestamp(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .username(push.user())
                .category(push.category())
                .message(truncate(push.message()))
                .details(push.details())
                .flightId(push.flightId())
                .flightNumber(push.flightNumber())
                .build();
        repository.save(entity);
    }

    private static String truncate(String message) {
        return message != null && message.length() > MESSAGE_MAX_LENGTH
                ? message.substring(0, MESSAGE_MAX_LENGTH)
                : message;
    }
}
