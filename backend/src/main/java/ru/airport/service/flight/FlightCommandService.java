package ru.airport.service.flight;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.FlightGenerationBusinessRules;
import ru.airport.business.FlightHomeAirportRules;
import ru.airport.business.FlightMutationBusinessRules;
import ru.airport.business.FlightPlanningTimeBusinessRules;
import ru.airport.business.ScheduleOccurrenceBusinessRules;
import ru.airport.config.AirportClock;
import ru.airport.config.AirportProperties;
import ru.airport.dto.FlightBulkDeleteRs;
import ru.airport.dto.FlightGenerateRq;
import ru.airport.dto.FlightGenerateRs;
import ru.airport.dto.FlightRq;
import ru.airport.dto.FlightRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Flight;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;
import ru.airport.repository.FlightRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.repository.ScheduleSlotRepository;
import ru.airport.websocket.RealtimeNotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static ru.airport.service.flight.FlightQuerySupport.touchCollections;

/**
 * Создание, обновление, удаление и пакетная генерация экземпляров рейсов.
 */
@Service
@RequiredArgsConstructor
public class FlightCommandService {

    private final FlightRepository flightRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final DtoMapper mapper;
    private final FlightMutationBusinessRules flightMutationBusinessRules;
    private final FlightGenerationBusinessRules flightGenerationBusinessRules;
    private final FlightPlanningTimeBusinessRules flightPlanningTimeBusinessRules;
    private final ScheduleOccurrenceBusinessRules scheduleOccurrenceBusinessRules;
    private final FlightHomeAirportRules flightHomeAirportRules;
    private final RealtimeNotificationService realtimeNotificationService;
    private final AirportProperties airportProperties;
    private final AirportClock airportClock;
    private final FlightQueryService flightQueryService;

    private String homeIata() {
        return airportProperties.getHomeIata();
    }

    @Transactional
    public FlightRs create(FlightRq rq) {
        ScheduleSlot slot = scheduleSlotRepository.findById(rq.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("ScheduleSlot", rq.getSlotId()));
        Schedule schedule = slot.getSchedule();
        flightMutationBusinessRules.assertNoExistingFlightForSlot(
                flightRepository.existsBySlot_SlotIdAndOperationDate(rq.getSlotId(), rq.getOperationDate()));
        scheduleOccurrenceBusinessRules.assertMatchesOperationDate(schedule, slot, rq.getOperationDate());
        flightHomeAirportRules.assertValidHomeRoute(schedule, homeIata());
        Flight flight = flightGenerationBusinessRules.buildFlight(schedule, slot, rq.getOperationDate());
        flightPlanningTimeBusinessRules.assertScheduledDepartureInFuture(
                flight.getScheduledDeparture(), airportClock.now());
        Flight saved = flightRepository.save(flight);
        FlightRs rs = mapper.toFlightRsSummary(saved);
        realtimeNotificationService.publishFlightCreated(rs);
        return rs;
    }

    @Transactional
    public FlightGenerateRs generate(FlightGenerateRq rq) {
        var now = airportClock.now();
        flightPlanningTimeBusinessRules.assertGenerationStartsTodayOrLater(
                rq.getFromDate(), now.toLocalDate());
        List<Schedule> schedules;
        if (rq.getScheduleId() != null) {
            Schedule schedule = scheduleRepository.findByIdWithSlots(rq.getScheduleId()).stream()
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Schedule", rq.getScheduleId()));
            schedules = List.of(schedule);
        } else {
            schedules = scheduleRepository.findAllActiveWithSlots();
        }

        // Один bulk-запрос вместо N+1 — формат ключа: slotId|operationDate
        Set<String> existingKeys = flightRepository.findSlotDateKeysInRange(
                rq.getFromDate(), rq.getToDate());

        FlightGenerationBusinessRules.GenerationPlan plan = flightGenerationBusinessRules.planGeneration(
                rq.getFromDate(),
                rq.getToDate(),
                schedules,
                (slotId, date) -> existingKeys.contains(slotId + "|" + date));

        List<Integer> flightIds = new ArrayList<>();
        int pastSkipped = 0;
        for (Flight draft : plan.toCreate()) {
            if (!flightPlanningTimeBusinessRules.isScheduledDepartureInFuture(draft.getScheduledDeparture(), now)) {
                pastSkipped++;
                continue;
            }
            flightHomeAirportRules.assertValidHomeRoute(draft.getSchedule(), homeIata());
            Flight saved = flightRepository.save(draft);
            flightIds.add(saved.getFlightId());
            realtimeNotificationService.publishFlightUpdate(mapper.toFlightRsSummary(saved));
        }

        return FlightGenerateRs.builder()
                .created(flightIds.size())
                .skipped(plan.skipped() + pastSkipped)
                .flightIds(flightIds)
                .build();
    }

    @Transactional
    public FlightRs update(Integer flightId, FlightRq rq) {
        Flight flight = flightQueryService.loadFlight(flightId);
        flightMutationBusinessRules.assertFlightEditable(flight.getStatus());
        flightMutationBusinessRules.assertSlotNotChanged(flight.getSlot().getSlotId(), rq.getSlotId());
        flightMutationBusinessRules.assertOperationDateNotChanged(flight.getOperationDate(), rq.getOperationDate());
        return mapper.toFlightRsSummary(flight);
    }

    @Transactional
    public void delete(Integer id) {
        Flight flight = flightQueryService.loadFlight(id);
        flightMutationBusinessRules.assertFlightDeletable(flight.getStatus());
        touchCollections(flight);
        int flightId = flight.getFlightId();
        flightRepository.delete(flight);
        realtimeNotificationService.publishFlightDeleted(flightId);
    }

    @Transactional
    public FlightBulkDeleteRs deleteBySchedule(Integer scheduleId) {
        // 1. Проверяем, что шаблон расписания вообще существует
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule", scheduleId));
                
        // 2. Загружаем рейсы ТОЛЬКО для проверки статусов (1 быстрый SELECT)
        // Коллекции gateAssignments и delayWarnings остаются LAZY и НЕ ТРОГАЮТСЯ
        List<Flight> flights = flightRepository.findBySchedule_ScheduleId(schedule.getScheduleId());
        
        // 3. Бизнес-проверка в памяти (работает мгновенно)
        List<Flight> blocked = flights.stream()
                .filter(f -> {
                    try {
                        flightMutationBusinessRules.assertFlightDeletable(f.getStatus());
                        return false;
                    } catch (RuntimeException ex) {
                        return true;
                    }
                })
                .toList();
                
        if (!blocked.isEmpty()) {
            String details = blocked.stream()
                    .map(f -> "#%d %s".formatted(f.getFlightId(), f.getStatus()))
                    .limit(10)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            throw new ru.airport.exception.ConflictException(
                    "Нельзя удалить все рейсы шаблона: есть рейсы не в статусах SCHEDULED/CANCELLED (" + details + ")");
        }
        
// 4. Если проверка прошла — бахаем ОДНИМ запросом (1 SQL DELETE)
        // База данных сама каскадно удалит гейты и задержки по внешнему ключу!
        List<Integer> flightIds = flights.stream().map(Flight::getFlightId).toList();
        int deletedCount = flightRepository.deleteByScheduleIdBulk(schedule.getScheduleId());

        for (Integer fid : flightIds) {
            realtimeNotificationService.publishFlightDeleted(fid);
        }

        return FlightBulkDeleteRs.builder()
                .scheduleId(schedule.getScheduleId())
                .deleted(deletedCount)
                .build();
    }
}
