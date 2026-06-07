package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.business.SchedulePeriodicityBusinessRules;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleRs;
import ru.airport.dto.ScheduleSlotRq;
import ru.airport.dto.ScheduleSlotRs;
import ru.airport.exception.BadRequestException;
import ru.airport.exception.ConflictException;
import ru.airport.model.Airline;
import ru.airport.model.Schedule;
import ru.airport.model.ScheduleSlot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/** Entity ↔ DTO для шаблонов расписания и слотов. */
@Component
public class ScheduleDtoMapper {

    private final SchedulePeriodicityBusinessRules periodicityRules;
    private final DirectoryDtoMapper directoryDtoMapper;

    public ScheduleDtoMapper(
            SchedulePeriodicityBusinessRules periodicityRules,
            DirectoryDtoMapper directoryDtoMapper) {
        this.periodicityRules = periodicityRules;
        this.directoryDtoMapper = directoryDtoMapper;
    }

    public ScheduleRs toScheduleRs(Schedule s) {
        return toScheduleRs(s, null);
    }

    public ScheduleRs toScheduleRs(Schedule s, LocalDate atDate) {
        if (s == null) {
            return null;
        }
        List<ScheduleSlotRs> slotRs = s.getSlots() != null
                ? s.getSlots().stream().map(this::toScheduleSlotRs).toList()
                : List.of();
        ScheduleRs.ScheduleRsBuilder builder = ScheduleRs.builder()
                .scheduleId(s.getScheduleId())
                .flightNumber(s.getFlightNumber())
                .originAirport(trimAirport(s.getOriginAirport()))
                .destinationAirport(trimAirport(s.getDestinationAirport()))
                .effectiveFrom(s.getEffectiveFrom())
                .effectiveTo(s.getEffectiveTo())
                .isActive(s.getIsActive())
                .periodicityType(s.getPeriodicityType())
                .periodicityStep(s.getPeriodicityStep())
                .airline(directoryDtoMapper.toAirlineRs(s.getAirline()))
                .slots(slotRs);
        if (atDate != null && s.getSlots() != null) {
            for (ScheduleSlot slot : s.getSlots()) {
                if (periodicityRules.matchesOperationDate(s, slot, atDate)) {
                    builder.departureAtDate(LocalDateTime.of(atDate, slot.getDepartureTime()));
                    LocalDate arrivalDate = atDate;
                    if (!slot.getArrivalTime().isAfter(slot.getDepartureTime())) {
                        arrivalDate = atDate.plusDays(1);
                    }
                    builder.arrivalAtDate(LocalDateTime.of(arrivalDate, slot.getArrivalTime()));
                    break;
                }
            }
        }
        return builder.build();
    }

    public ScheduleSlotRs toScheduleSlotRs(ScheduleSlot slot) {
        if (slot == null) {
            return null;
        }
        return ScheduleSlotRs.builder()
                .slotId(slot.getSlotId())
                .dayOfWeek(slot.getDayOfWeek())
                .departureTime(slot.getDepartureTime())
                .arrivalTime(slot.getArrivalTime())
                .build();
    }

    public Schedule newSchedule(ScheduleRq rq, Airline airline) {
        Schedule schedule = Schedule.builder()
                .flightNumber(rq.getFlightNumber())
                .originAirport(rq.getOriginAirport())
                .destinationAirport(rq.getDestinationAirport())
                .effectiveFrom(rq.getEffectiveFrom())
                .effectiveTo(rq.getEffectiveTo())
                .isActive(rq.getIsActive() != null ? rq.getIsActive() : true)
                .periodicityType(rq.getPeriodicityType())
                .periodicityStep(rq.getPeriodicityStep())
                .airline(airline)
                .build();
        applySlots(rq.getSlots(), schedule);
        return schedule;
    }

    public void apply(ScheduleRq rq, Schedule s, Airline airline) {
        applyFields(rq, s, airline);
    }

    public void applyFields(ScheduleRq rq, Schedule s, Airline airline) {
        s.setFlightNumber(rq.getFlightNumber());
        s.setOriginAirport(rq.getOriginAirport());
        s.setDestinationAirport(rq.getDestinationAirport());
        s.setEffectiveFrom(rq.getEffectiveFrom());
        s.setEffectiveTo(rq.getEffectiveTo());
        if (rq.getIsActive() != null) {
            s.setIsActive(rq.getIsActive());
        }
        s.setPeriodicityType(rq.getPeriodicityType());
        s.setPeriodicityStep(rq.getPeriodicityStep());
        s.setAirline(airline);
    }

    /** Обновляет слоты in-place по slotId; новые без id — insert; лишние — delete если нет рейсов. */
    public void mergeSlots(List<ScheduleSlotRq> slotRqs, Schedule schedule, IntPredicate slotHasFlights) {
        if (slotRqs == null) {
            return;
        }
        Map<Integer, ScheduleSlot> existingById = schedule.getSlots().stream()
                .filter(s -> s.getSlotId() != null)
                .collect(Collectors.toMap(ScheduleSlot::getSlotId, s -> s));
        Set<Integer> keptIds = new HashSet<>();

        for (ScheduleSlotRq rq : slotRqs) {
            if (rq.getSlotId() != null) {
                ScheduleSlot slot = existingById.get(rq.getSlotId());
                if (slot == null) {
                    throw new BadRequestException("Слот с id " + rq.getSlotId() + " не найден в расписании");
                }
                slot.setDayOfWeek(rq.getDayOfWeek());
                slot.setDepartureTime(rq.getDepartureTime());
                slot.setArrivalTime(rq.getArrivalTime());
                keptIds.add(rq.getSlotId());
            } else {
                schedule.getSlots().add(ScheduleSlot.builder()
                        .dayOfWeek(rq.getDayOfWeek())
                        .departureTime(rq.getDepartureTime())
                        .arrivalTime(rq.getArrivalTime())
                        .schedule(schedule)
                        .build());
            }
        }

        List<ScheduleSlot> toRemove = schedule.getSlots().stream()
                .filter(s -> s.getSlotId() != null && !keptIds.contains(s.getSlotId()))
                .toList();
        for (ScheduleSlot orphan : toRemove) {
            if (slotHasFlights.test(orphan.getSlotId())) {
                throw new ConflictException(
                        "Нельзя удалить слот: по нему уже созданы рейсы. Оставьте слот в форме или удалите рейсы.");
            }
            schedule.getSlots().remove(orphan);
        }
    }

    private void applySlots(List<ScheduleSlotRq> slotRqs, Schedule schedule) {
        if (slotRqs == null) {
            return;
        }
        List<ScheduleSlot> slots = new ArrayList<>();
        for (ScheduleSlotRq rq : slotRqs) {
            ScheduleSlot slot = ScheduleSlot.builder()
                    .dayOfWeek(rq.getDayOfWeek())
                    .departureTime(rq.getDepartureTime())
                    .arrivalTime(rq.getArrivalTime())
                    .schedule(schedule)
                    .build();
            slots.add(slot);
        }
        schedule.getSlots().addAll(slots);
    }

    private static String trimAirport(String code) {
        return code == null ? null : code.trim();
    }
}
