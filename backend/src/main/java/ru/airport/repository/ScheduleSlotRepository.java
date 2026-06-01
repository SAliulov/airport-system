package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.ScheduleSlot;

import java.util.List;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Integer> {

    List<ScheduleSlot> findBySchedule_ScheduleIdOrderBySlotIdAsc(Integer scheduleId);
}
