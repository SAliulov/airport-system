package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Справочник гейтов аэропорта.
 *
 * is_active — гейт может быть закрыт на ремонт;
 * неактивный гейт нельзя назначать рейсам (проверка в GateService).
 *
 * max_size_category — физическое ограничение гейта:
 * ВС с большей категорией не может быть сюда назначено.
 */
@Entity
@Table(name = "gate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "assignments")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Gate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "gate_id")
    @EqualsAndHashCode.Include
    private Integer gateId;

    /**
     * Бизнес-идентификатор вида "A12", "B3".
     * Уникален, не может быть null.
     */
    @Column(name = "gate_number", length = 10, nullable = false, unique = true)
    private String gateNumber;

    /** Терминал, в котором находится гейт ("A", "B", "D"). */
    @Column(name = "terminal", length = 10)
    private String terminal;

    /**
     * Признак активности гейта.
     * false — гейт закрыт (ремонт, реконструкция), назначение запрещено.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Максимальная категория ВС, которую физически принимает гейт.
     * Хранится как строка в БД (VARCHAR(10)).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "max_size_category", length = 10)
    private SizeCategory maxSizeCategory;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * ON DELETE RESTRICT — нельзя удалить гейт, пока у него есть назначения.
     * История назначений сохраняется в gate_assignment.
     */
    @OneToMany(mappedBy = "gate", fetch = FetchType.LAZY)
    @Builder.Default
    private List<GateAssignment> assignments = new ArrayList<>();
}