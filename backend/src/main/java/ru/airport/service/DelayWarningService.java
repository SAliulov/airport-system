package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.DelayWarning;
import ru.airport.repository.DelayWarningRepository;
import ru.airport.repository.DelayWarningSpecifications;
import ru.airport.websocket.RealtimeNotificationService;

import java.time.LocalDate;
import java.util.List;

import static ru.airport.service.flight.FlightQuerySupport.normalizeSearchQuery;

/**
 * Сводный список и управление (редактирование/отмена) предупреждениями о задержках по всем
 * рейсам — вкладка «Задержки» диспетчера. Создание по-прежнему живёт в {@code FlightResourceService}
 * (привязано к одному рейсу, требует статус DELAYED — {@link ru.airport.business.DelayWarningBusinessRules}).
 */
@Service
@RequiredArgsConstructor
public class DelayWarningService {

    private final DelayWarningRepository delayWarningRepository;
    private final FlightMutationBusinessRules flightMutationBusinessRules;
    private final DtoMapper mapper;
    private final AirportClock airportClock;
    private final RealtimeNotificationService realtimeNotificationService;

    @Transactional(readOnly = true)
    public List<DelayWarningRs> list(LocalDate date, Integer airlineId, String flightNumberQuery) {
        var spec = DelayWarningSpecifications.forManagementList(
                airportClock.startOfDay(date),
                airportClock.startOfNextDay(date),
                airlineId,
                normalizeSearchQuery(flightNumberQuery));
        return delayWarningRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(mapper::toDelayWarningRs)
                .toList();
    }

    @Transactional
    public DelayWarningRs updateDelayWarning(Integer warningId, DelayWarningRq rq) {
        DelayWarning warning = loadWarning(warningId);
        flightMutationBusinessRules.assertFlightEditable(warning.getFlight().getStatus());
        warning.setDelayMinutes(rq.getDelayMinutes());
        warning.setReason(rq.getReason());
        DelayWarningRs rs = mapper.toDelayWarningRs(delayWarningRepository.save(warning));
        realtimeNotificationService.publishDelayWarning(warning.getFlight().getFlightId(), rs, "UPDATED");
        return rs;
    }

    @Transactional
    public DelayWarningRs deleteDelayWarning(Integer warningId) {
        DelayWarning warning = loadWarning(warningId);
        flightMutationBusinessRules.assertFlightEditable(warning.getFlight().getStatus());
        Integer flightId = warning.getFlight().getFlightId();
        DelayWarningRs rs = mapper.toDelayWarningRs(warning);
        delayWarningRepository.delete(warning);
        realtimeNotificationService.publishDelayWarning(flightId, rs, "DELETED");
        return rs;
    }

    private DelayWarning loadWarning(Integer warningId) {
        return delayWarningRepository.findById(warningId)
                .orElseThrow(() -> new ResourceNotFoundException("DelayWarning", warningId));
    }
}
