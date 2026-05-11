package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.AirlineBusinessRules;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.AirlineRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Airline;
import ru.airport.repository.AirlineRepository;
import ru.airport.repository.ScheduleRepository;
import ru.airport.validation.TextNormalization;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AirlineService {

    public static final String CACHE_AIRLINES = "airlines";

    private final AirlineRepository airlineRepository;
    private final ScheduleRepository scheduleRepository;
    private final DtoMapper mapper;
    private final AirlineBusinessRules airlineBusinessRules;

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
        TextNormalization.normalizeAirlineCodes(rq);
        airlineBusinessRules.assertIataUniqueForCreate(
                airlineRepository.existsByIataCode(rq.getIataCode()), rq.getIataCode());
        Airline saved = airlineRepository.save(mapper.newAirline(rq));
        return mapper.toAirlineRs(saved);
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRLINES, allEntries = true)
    public AirlineRs update(Integer id, AirlineRq rq) {
        TextNormalization.normalizeAirlineCodes(rq);
        Airline a = loadAirline(id);
        Integer otherId = airlineRepository.findByIataCode(rq.getIataCode())
                .map(Airline::getAirlineId)
                .orElse(null);
        airlineBusinessRules.assertIataUniqueForUpdate(id, rq.getIataCode(), otherId);
        mapper.apply(rq, a);
        return mapper.toAirlineRs(airlineRepository.save(a));
    }

    @Transactional
    @CacheEvict(cacheNames = CACHE_AIRLINES, allEntries = true)
    public void delete(Integer id) {
        loadAirline(id);
        airlineBusinessRules.assertMayDelete(scheduleRepository.existsByAirline_AirlineId(id));
        airlineRepository.deleteById(id);
    }

    public Airline getReferenceById(Integer id) {
        return loadAirline(id);
    }

    private Airline loadAirline(Integer id) {
        return airlineRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Airline", id));
    }
}
