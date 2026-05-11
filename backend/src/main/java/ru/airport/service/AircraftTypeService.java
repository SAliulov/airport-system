package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.AircraftTypeBusinessRules;
import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.AircraftType;
import ru.airport.repository.AircraftTypeRepository;
import ru.airport.validation.TextNormalization;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AircraftTypeService {

    public static final String CACHE_AIRCRAFT_TYPES = "aircraftTypes";

    private final AircraftTypeRepository aircraftTypeRepository;
    private final DtoMapper mapper;
    private final AircraftTypeBusinessRules aircraftTypeBusinessRules;

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
        TextNormalization.normalizeAircraftTypeCodes(rq);
        aircraftTypeBusinessRules.assertIcaoUniqueForCreate(
                aircraftTypeRepository.existsByIcaoCode(rq.getIcaoCode()), rq.getIcaoCode());
        AircraftType saved = aircraftTypeRepository.save(mapper.newAircraftType(rq));
        return mapper.toAircraftTypeRs(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRCRAFT_TYPES, allEntries = true)
    public AircraftTypeRs update(Integer id, AircraftTypeRq rq) {
        TextNormalization.normalizeAircraftTypeCodes(rq);
        AircraftType t = loadType(id);
        Integer otherId = aircraftTypeRepository.findByIcaoCode(rq.getIcaoCode())
                .map(AircraftType::getAircraftTypeId)
                .orElse(null);
        aircraftTypeBusinessRules.assertIcaoUniqueForUpdate(id, rq.getIcaoCode(), otherId);
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
