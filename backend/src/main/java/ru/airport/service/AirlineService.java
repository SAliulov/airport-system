package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.AirlineRs;
import ru.airport.exception.ConflictException;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Airline;
import ru.airport.repository.AirlineRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirlineService {

    public static final String CACHE_AIRLINES = "airlines";

    private final AirlineRepository airlineRepository;
    private final DtoMapper mapper;

    @Cacheable(cacheNames = CACHE_AIRLINES, key = "'all'")
    public List<AirlineRs> findAll() {
        return airlineRepository.findAll(Sort.by("name")).stream()
                .map(mapper::toAirlineRs)
                .toList();
    }

    public AirlineRs getById(Integer id) {
        return mapper.toAirlineRs(loadAirline(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRLINES, allEntries = true)
    public AirlineRs create(AirlineRq rq) {
        if (airlineRepository.existsByIataCode(rq.getIataCode())) {
            throw new ConflictException("IATA-код уже занят: " + rq.getIataCode());
        }
        Airline saved = airlineRepository.save(mapper.newAirline(rq));
        return mapper.toAirlineRs(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRLINES, allEntries = true)
    public AirlineRs update(Integer id, AirlineRq rq) {
        Airline a = loadAirline(id);
        airlineRepository.findByIataCode(rq.getIataCode()).ifPresent(other -> {
            if (!other.getAirlineId().equals(id)) {
                throw new ConflictException("IATA-код уже занят: " + rq.getIataCode());
            }
        });
        mapper.apply(rq, a);
        return mapper.toAirlineRs(airlineRepository.save(a));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRLINES, allEntries = true)
    public void delete(Integer id) {
        airlineRepository.delete(loadAirline(id));
    }

    public Airline getReferenceById(Integer id) {
        return loadAirline(id);
    }

    private Airline loadAirline(Integer id) {
        return airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline", id));
    }
}
