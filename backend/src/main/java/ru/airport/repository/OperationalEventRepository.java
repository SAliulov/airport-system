package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.OperationalEvent;

public interface OperationalEventRepository extends JpaRepository<OperationalEvent, Long> {
}
