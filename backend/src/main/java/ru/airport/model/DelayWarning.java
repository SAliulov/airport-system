package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Предупреждение о задержке рейса (задача 8).
 *
 * На один рейс может быть несколько записей по мере развития ситуации:
 * сначала задержка 30 минут, потом уточнили до 60.
 *
 * При создании записи сервис отправляет WebSocket push-уведомление
 * мобильным клиентам (GoF: Observer).
 */
@Entity
@Table(name = "delay_warning")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "flight")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DelayWarning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "warning_id")
    @EqualsAndHashCode.Include
    private Integer warningId;

    /** Величина задержки в минутах. */
    @Column(name = "delay_minutes", nullable = false)
    private Integer delayMinutes;

    /**
     * Причина задержки.
     * NULL допустим — причина может быть неизвестна в момент создания предупреждения.
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * Время создания предупреждения.
     * Устанавливается в сервисном слое перед сохранением.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * Рейс, для которого создано предупреждение.
     * NOT NULL. ON DELETE CASCADE — удалён рейс → удалены все предупреждения.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;
}