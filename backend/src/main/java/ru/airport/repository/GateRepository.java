package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.Gate;
import ru.airport.model.SizeCategory;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий гейтов аэропорта.
 */
public interface GateRepository extends JpaRepository<Gate, Integer> {

    Optional<Gate> findByGateNumber(String gateNumber);

    boolean existsByGateNumber(String gateNumber);

    /** Все активные гейты — для отображения в форме назначения. */
    List<Gate> findByIsActiveTrue();

    /**
     * Активные гейты, которые физически принимают данную категорию ВС.
     * Используется при проверке совместимости (задача 4/5):
     * гейт подходит если его maxSizeCategory >= категории ВС.
     *
     * Hibernate транслирует сравнение enum через ordinal в БД,
     * поэтому используем явный JPQL с ordinal().
     */
    List<Gate> findByIsActiveTrueAndMaxSizeCategoryGreaterThanEqual(SizeCategory sizeCategory);
}
