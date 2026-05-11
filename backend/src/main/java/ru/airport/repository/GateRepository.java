package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.airport.model.Gate;
import ru.airport.model.SizeCategory;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий гейтов аэропорта.
 * Удаление: методы {@link JpaRepository#delete}, {@link JpaRepository#deleteById}.
 */
public interface GateRepository extends JpaRepository<Gate, Integer> {

    Optional<Gate> findByGateNumber(String gateNumber);

    boolean existsByGateNumber(String gateNumber);

    /** Все активные гейты — для отображения в форме назначения. */
    List<Gate> findByIsActiveTrue();

    /**
     * Активные гейты, совместимые с категорией ВС по правилу
     * {@link SizeCategory#isCompatible(SizeCategory, SizeCategory)}.
     * <p>
     * Для {@code @Enumerated(STRING)} нельзя использовать сравнение
     * {@code GreaterThanEqual} в SQL — порядок строк не совпадает с NARROW &lt; WIDE &lt; JUMBO.
     */
    @Query("""
            SELECT g FROM Gate g
            WHERE g.isActive = true
              AND (
                   g.maxSizeCategory = ru.airport.model.SizeCategory.JUMBO
                OR (g.maxSizeCategory = ru.airport.model.SizeCategory.WIDE
                    AND :aircraftSize IN (ru.airport.model.SizeCategory.NARROW, ru.airport.model.SizeCategory.WIDE))
                OR (g.maxSizeCategory = ru.airport.model.SizeCategory.NARROW
                    AND :aircraftSize = ru.airport.model.SizeCategory.NARROW)
              )
            ORDER BY g.gateNumber
            """)
    List<Gate> findActiveGatesCompatibleWithAircraftSize(@Param("aircraftSize") SizeCategory aircraftSize);
}
