package ru.airport.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.airport.business.GateBusinessRules;
import ru.airport.dto.GateRq;
import ru.airport.dto.GateRs;
import ru.airport.dto.GateTimelineSegmentRs;
import ru.airport.exception.ResourceNotFoundException;
import ru.airport.mapper.DtoMapper;
import ru.airport.model.Gate;
import ru.airport.repository.GateAssignmentRepository;
import ru.airport.repository.GateRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GateService {

    private final GateRepository gateRepository;
    private final GateAssignmentRepository gateAssignmentRepository;
    private final DtoMapper mapper;
    private final GateBusinessRules gateBusinessRules;

    public List<GateRs> findAll() {
        return gateRepository.findAll(Sort.by("gateNumber")).stream()
                .map(mapper::toGateRs)
                .toList();
    }

    public List<GateRs> findActive() {
        return gateRepository.findByIsActiveTrue().stream()
                .map(mapper::toGateRs)
                .toList();
    }

    public GateRs getById(Integer id) {
        return mapper.toGateRs(loadGate(id));
    }

    @Transactional
    public GateRs create(GateRq rq) {
        gateBusinessRules.assertGateNumberUniqueForCreate(
                gateRepository.existsByGateNumber(rq.getGateNumber()), rq.getGateNumber());
        Gate saved = gateRepository.save(mapper.newGate(rq));
        return mapper.toGateRs(saved);
    }

    @Transactional
    public GateRs update(Integer id, GateRq rq) {
        Gate g = loadGate(id);
        Integer otherId = gateRepository.findByGateNumber(rq.getGateNumber())
                .map(Gate::getGateId)
                .orElse(null);
        gateBusinessRules.assertGateNumberUniqueForUpdate(id, rq.getGateNumber(), otherId);
        mapper.apply(rq, g);
        return mapper.toGateRs(gateRepository.save(g));
    }

    @Transactional
    public void delete(Integer id) {
        Gate gate = loadGate(id);
        gateBusinessRules.assertMayDelete(gateAssignmentRepository.existsByGate_GateId(id));
        gateRepository.delete(gate);
    }

    /**
     * Интервалы занятости гейтов за календарные сутки (задача 7).
     */
    public List<GateTimelineSegmentRs> getTimelineForDay(LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return gateAssignmentRepository.findByDay(dayStart, dayEnd).stream()
                .map(mapper::toTimelineSegment)
                .toList();
    }

    public Gate getReferenceById(Integer id) {
        return loadGate(id);
    }

    private Gate loadGate(Integer id) {
        return gateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gate", id));
    }
}
