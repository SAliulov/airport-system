package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.ScheduleSlot;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Integer> {
}
