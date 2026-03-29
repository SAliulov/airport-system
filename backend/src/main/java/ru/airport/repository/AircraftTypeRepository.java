package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.AircraftType;
import ru.airport.model.SizeCategory;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий типов воздушных судов.
 * Кэшируется в сервисном слое через @Cacheable.
 */
public interface AircraftTypeRepository extends JpaRepository<AircraftType, Integer> {

    Optional<AircraftType> findByIcaoCode(String icaoCode);

    boolean existsByIcaoCode(String icaoCode);

    List<AircraftType> findBySizeCategory(SizeCategory sizeCategory);
}
