package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Конкретный выполняемый рейс — один экземпляр из расписания.
 *
 * Пример: если {@link Schedule} — "SU100 каждый день",
 * то Flight — "SU100 от 16 марта 2026".
 *
 * Центральная сущность системы. Через неё проходят:
 * — автообновление статуса (@Scheduled, задача 3)
 * — назначение гейта (задача 4)
 * — назначение типа ВС (задача 5)
 * — предупреждения о задержке (задача 8)
 *
 * GRASP: Information Expert — FlightService работает именно с этой сущностью.
 * GoF: Observer — изменение статуса → WebSocket-событие всем клиентам.
 */
@Entity
@Table(name = "flight")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"schedule", "slot", "aircraftType", "gateAssignments", "delayWarnings"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flight_id")
    @EqualsAndHashCode.Include
    private Integer flightId;

    /**
     * Фактическое время вылета.
     * NULL до момента вылета самолёта.
     */
    @Column(name = "actual_departure")
    private LocalDateTime actualDeparture;

    /**
     * Фактическое время прилёта.
     * NULL до момента посадки.
     */
    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    /**
     * Текущий статус рейса.
     * Хранится как строка (VARCHAR(20)) — читаемо в БД без справочника.
     * Начальное значение SCHEDULED устанавливается при создании.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private FlightStatus status = FlightStatus.SCHEDULED;

    /** Дата выполнения рейса (календарный день операции). */
    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    /** Плановое время вылета на {@link #operationDate}. */
    @Column(name = "scheduled_departure", nullable = false)
    private LocalDateTime scheduledDeparture;

    /** Плановое время прилёта (может быть на следующий календарный день). */
    @Column(name = "scheduled_arrival", nullable = false)
    private LocalDateTime scheduledArrival;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * Слот шаблона, из которого создан экземпляр.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private ScheduleSlot slot;

    /**
     * Шаблон расписания (денормализованная связь для запросов).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    /**
     * Назначенный тип ВС.
     * NULL пока ВС не назначено (назначается после создания рейса, задача 5).
     * ON DELETE SET NULL — если тип ВС удалён из справочника, рейс остаётся.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    /**
     * История назначений гейтов на этот рейс.
     * Без orphanRemoval: смена гейта — только добавление новой записи; очистка коллекции не удаляет историю из БД.
     * При удалении рейса дочерние назначения удаляются каскадом (JPA REMOVE + ON DELETE CASCADE в БД).
     */
    @OneToMany(mappedBy = "flight", fetch = FetchType.LAZY,
            cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE })
    @OrderBy("assignmentId ASC")
    @BatchSize(size = 32)
    @Builder.Default
    private List<GateAssignment> gateAssignments = new ArrayList<>();

    /**
     * Предупреждения о задержке этого рейса.
     * Без orphanRemoval — несколько записей во времени; не удалять историю при манипуляциях с коллекцией.
     */
    @OneToMany(mappedBy = "flight", fetch = FetchType.LAZY,
            cascade = { CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE })
    @OrderBy("createdAt ASC")
    @BatchSize(size = 32)
    @Builder.Default
    private List<DelayWarning> delayWarnings = new ArrayList<>();

    // ───── Вспомогательные методы ─────────────────────────────────────────

    /**
     * Возвращает последнее (актуальное) назначение гейта по порядку {@code assignmentId},
     * либо null если гейт ещё не назначен.
     * Используется в сервисном слое — не в контроллере.
     */
    public GateAssignment getActiveGateAssignment() {
        if (gateAssignments == null || gateAssignments.isEmpty()) {
            return null;
        }
        return gateAssignments.get(gateAssignments.size() - 1);
    }
}