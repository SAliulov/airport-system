package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Назначение гейта на рейс с временным интервалом.
 *
 * Ключевая таблица для задачи 4 — проверки занятости гейта:
 * перед назначением сервис проверяет пересечение интервалов
 * [assigned_from, assigned_to] для данного gate_id.
 *
 * Если диспетчер меняет гейт — старая запись остаётся как история,
 * создаётся новая. Это позволяет видеть всю историю назначений.
 *
 * Также является источником данных для задачи 7 —
 * визуализации интервалов между рейсами на гейтах (timeline).
 */
@Entity
@Table(name = "gate_assignment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"flight", "gate"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GateAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    @EqualsAndHashCode.Include
    private Integer assignmentId;

    /**
     * Начало занятости гейта этим рейсом.
     * Обычно совпадает с scheduled_departure или чуть раньше (посадка).
     */
    @Column(name = "assigned_from", nullable = false)
    private LocalDateTime assignedFrom;

    /**
     * Конец занятости гейта этим рейсом.
     * Обычно совпадает с scheduled_departure (или фактическим вылетом).
     */
    @Column(name = "assigned_to", nullable = false)
    private LocalDateTime assignedTo;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * Рейс, для которого назначен гейт.
     * NOT NULL. ON DELETE CASCADE — удалён рейс → удалены все его назначения.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    /**
     * Гейт, назначенный на рейс.
     * NOT NULL. ON DELETE RESTRICT — нельзя удалить гейт с активными назначениями.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "gate_id", nullable = false)
    private Gate gate;
}