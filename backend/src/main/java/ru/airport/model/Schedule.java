package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Плановое расписание рейсов.
 *
 * Представляет шаблон рейса (например, "SU100 Москва→Питер каждый день").
 * Конкретный факт выполнения рейса — отдельная сущность {@link Flight}.
 *
 * GRASP: Information Expert — знает плановые параметры рейса.
 */
@Entity
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"airline", "flights"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    @EqualsAndHashCode.Include
    private Integer scheduleId;

    /** Номер рейса: SU100, U6204 и т.д. */
    @Column(name = "flight_number", length = 20, nullable = false)
    private String flightNumber;

    /** IATA-код аэропорта вылета (SVO, LED, JFK). CHAR(3). */
    @Column(name = "origin_airport", columnDefinition = "char(3)", nullable = false)
    private String originAirport;

    /** IATA-код аэропорта прилёта. CHAR(3). */
    @Column(name = "destination_airport", columnDefinition = "char(3)", nullable = false)
    private String destinationAirport;

    /** Плановое время вылета. */
    @Column(name = "scheduled_departure", nullable = false)
    private LocalDateTime scheduledDeparture;

    /** Плановое время прилёта. */
    @Column(name = "scheduled_arrival", nullable = false)
    private LocalDateTime scheduledArrival;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * Авиакомпания-владелец рейса.
     * NOT NULL, ON DELETE RESTRICT — рейс без авиакомпании существовать не может.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airline_id", nullable = false)
    private Airline airline;

    /**
     * Конкретные выполнения этого расписания.
     * CascadeType не задан: flight живёт независимо (ON DELETE RESTRICT в БД).
     */
    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Flight> flights = new ArrayList<>();
}