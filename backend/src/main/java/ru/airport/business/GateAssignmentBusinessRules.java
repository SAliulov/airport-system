package ru.airport.business;

import ru.airport.exception.BadRequestException;
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

    private final int planWindowHours;
    private final int postGraceMinutes;

    public GateAssignmentBusinessRules(int planWindowHours, int postGraceMinutes) {
        this.planWindowHours = planWindowHours;
        this.postGraceMinutes = postGraceMinutes;
    }

    public void assertValidInterval(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new ConflictException(
                    "Интервал назначения гейта некорректен: assigned_from должен быть строго раньше assigned_to");
        }
    }

    public void assertAssignmentStartsNotInPast(LocalDateTime assignedFrom, LocalDateTime now) {
        if (assignedFrom != null && now != null && assignedFrom.isBefore(now)) {
            throw new BadRequestException(
                    "Интервал назначения гейта не может начинаться в прошлом по времени аэропорта (" + now + ")");
        }
    }

    /**
     * Интервал гейта должен пересекаться с окном ±planWindowHours вокруг планового якоря рейса.
     */
    public void assertIntervalOverlapsScheduledWindow(
            LocalDateTime assignedFrom,
            LocalDateTime assignedTo,
            LocalDateTime scheduledAnchor) {
        if (scheduledAnchor == null) {
            return;
        }
        LocalDateTime windowStart = scheduledAnchor.minusHours(planWindowHours);
        LocalDateTime windowEnd = scheduledAnchor.plusHours(planWindowHours);
        boolean overlaps = assignedFrom.isBefore(windowEnd) && assignedTo.isAfter(windowStart);
        if (!overlaps) {
            throw new BadRequestException(
                    "Интервал гейта должен пересекаться с плановым временем рейса (±" + planWindowHours + " ч)");
        }
    }

    /**
     * Фактическое время вылета/прилёта должно быть после начала занятости гейта
     * и не позже assigned_to + postGrace.
     */
    public void assertActualTimeWithinGateInterval(
            LocalDateTime actualTime,
            GateAssignment assignment,
            String label) {
        if (actualTime == null || assignment == null) {
            return;
        }
        LocalDateTime from = assignment.getAssignedFrom();
        LocalDateTime to = assignment.getAssignedTo();
        if (from == null || to == null) {
            return;
        }
        LocalDateTime maxAllowed = to.plusMinutes(postGraceMinutes);
        if (!actualTime.isAfter(from)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть раньше начала занятости гейта ("
                            + from + ")");
        }
        if (actualTime.isAfter(maxAllowed)) {
            throw new BadRequestException(
                    "Фактическое время " + label + " не может быть позже окончания занятости гейта более чем на "
                            + postGraceMinutes + " мин");
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
