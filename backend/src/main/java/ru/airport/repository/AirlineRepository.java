package ru.airport.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.airport.model.Airline;

import java.util.Optional;

/**
 * Репозиторий авиакомпаний.
 * Стандартный CRUD предоставляет JpaRepository.
 * Кэшируется в сервисном слое через @Cacheable.
 */
public interface AirlineRepository extends JpaRepository<Airline, Integer> {

    Optional<Airline> findByIataCode(String iataCode);

    boolean existsByIataCode(String iataCode);
}
