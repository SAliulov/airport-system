package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.airport.model.DelayWarning;

import java.util.List;

/**
 * Репозиторий предупреждений о задержке рейса.
 */
public interface DelayWarningRepository
        extends JpaRepository<DelayWarning, Integer>, JpaSpecificationExecutor<DelayWarning> {

    /**
     * Все предупреждения для рейса, отсортированные по времени создания.
     * Используется для отображения истории задержек рейса (задача 8).
     */
    List<DelayWarning> findByFlight_FlightIdOrderByCreatedAtDesc(Integer flightId);
}
