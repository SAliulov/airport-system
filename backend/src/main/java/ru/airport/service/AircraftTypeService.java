package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.exception.ConflictException;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.AircraftType;
import ru.airport.repository.AircraftTypeRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AircraftTypeService {

    public static final String CACHE_AIRCRAFT_TYPES = "aircraftTypes";

    private final AircraftTypeRepository aircraftTypeRepository;
    private final DtoMapper mapper;

    @Cacheable(cacheNames = CACHE_AIRCRAFT_TYPES, key = "'all'")
    public List<AircraftTypeRs> findAll() {
        return aircraftTypeRepository.findAll(Sort.by("icaoCode")).stream()
                .map(mapper::toAircraftTypeRs)
                .toList();
    }

    public AircraftTypeRs getById(Integer id) {
        return mapper.toAircraftTypeRs(loadType(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRCRAFT_TYPES, allEntries = true)
    public AircraftTypeRs create(AircraftTypeRq rq) {
        if (aircraftTypeRepository.existsByIcaoCode(rq.getIcaoCode())) {
            throw new ConflictException("Код ICAO уже занят: " + rq.getIcaoCode());
        }
        AircraftType saved = aircraftTypeRepository.save(mapper.newAircraftType(rq));
        return mapper.toAircraftTypeRs(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRCRAFT_TYPES, allEntries = true)
    public AircraftTypeRs update(Integer id, AircraftTypeRq rq) {
        AircraftType t = loadType(id);
        aircraftTypeRepository.findByIcaoCode(rq.getIcaoCode()).ifPresent(other -> {
            if (!other.getAircraftTypeId().equals(id)) {
                throw new ConflictException("Код ICAO уже занят: " + rq.getIcaoCode());
            }
        });
        mapper.apply(rq, t);
        return mapper.toAircraftTypeRs(aircraftTypeRepository.save(t));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRCRAFT_TYPES, allEntries = true)
    public void delete(Integer id) {
        aircraftTypeRepository.delete(loadType(id));
    }

    public AircraftType getReferenceById(Integer id) {
        return loadType(id);
    }

    private AircraftType loadType(Integer id) {
        return aircraftTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AircraftType", id));
    }
}
