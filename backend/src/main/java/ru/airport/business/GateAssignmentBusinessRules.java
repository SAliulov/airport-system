package ru.airport.business;

import ru.airport.exception.ConflictException;
import ru.airport.model.AircraftType;
import ru.airport.model.Gate;
import ru.airport.model.GateAssignment;
import ru.airport.model.SizeCategory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Правила назначения гейта: интервал времени, активность гейта, отсутствие пересечений, размер ВС.
 */
public class GateAssignmentBusinessRules {

    public void assertValidInterval(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ConflictException(
                    "Интервал назначения гейта некорректен: assigned_from должен быть строго раньше assigned_to");
        }
    }

    public void assertGateIsActive(Gate gate) {
        if (!Boolean.TRUE.equals(gate.getIsActive())) {
            throw new ConflictException("Гейт неактивен: " + gate.getGateNumber());
        }
    }

    public void assertNoOverlaps(List<GateAssignment> overlaps) {
        if (overlaps != null && !overlaps.isEmpty()) {
            throw new ConflictException("Гейт занят в указанный интервал времени (пересечение с другим рейсом)");
        }
    }

    public void assertAircraftFitsGate(AircraftType aircraft, Gate gate) {
        if (aircraft == null) {
            return;
        }
        if (!SizeCategory.isCompatible(aircraft.getSizeCategory(), gate.getMaxSizeCategory())) {
            throw new ConflictException(
                    "Категория размера ВС несовместима с гейтом " + gate.getGateNumber());
        }
    }

    /**
     * Закрывает «хвост» предыдущих назначений рейса перед новым интервалом (смена гейта или времени).
     */
    public void closePriorAssignmentsForFlight(List<GateAssignment> assignments, LocalDateTime newFrom) {
        if (assignments == null || newFrom == null) {
            return;
        }
        for (GateAssignment ga : assignments) {
            if (ga.getAssignedTo() == null || !ga.getAssignedTo().isAfter(newFrom)) {
                continue;
            }
            ga.setAssignedTo(newFrom);
            if (!ga.getAssignedFrom().isBefore(ga.getAssignedTo())) {
                ga.setAssignedTo(ga.getAssignedFrom().plusMinutes(1));
            }
        }
    }

    /** Освобождает гейт при отмене / завершении: обрезает активное назначение до {@code endAt}. */
    public void closeActiveAssignmentAt(GateAssignment assignment, LocalDateTime endAt) {
        if (assignment == null || endAt == null) {
            return;
        }
        if (assignment.getAssignedTo() != null && assignment.getAssignedTo().isAfter(endAt)) {
            assignment.setAssignedTo(endAt);
            if (!assignment.getAssignedFrom().isBefore(assignment.getAssignedTo())) {
                assignment.setAssignedTo(assignment.getAssignedFrom().plusMinutes(1));
            }
        }
    }
}
