package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Шаблон рейса: маршрут, периодичность, сезон действия.
 * Конкретные вылеты — {@link Flight} через {@link ScheduleSlot}.
 */
@Entity
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"airline", "slots", "flights"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    @EqualsAndHashCode.Include
    private Integer scheduleId;

    @Column(name = "flight_number", length = 20, nullable = false)
    private String flightNumber;

    @Column(name = "origin_airport", columnDefinition = "char(3)", nullable = false)
    private String originAirport;

    @Column(name = "destination_airport", columnDefinition = "char(3)", nullable = false)
    private String destinationAirport;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "periodicity_type", length = 20, nullable = false)
    @Builder.Default
    private PeriodicityType periodicityType = PeriodicityType.WEEKLY;

    @Column(name = "periodicity_step", nullable = false)
    @Builder.Default
    private Integer periodicityStep = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airline_id", nullable = false)
    private Airline airline;

    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("slotId ASC")
    @Builder.Default
    private List<ScheduleSlot> slots = new ArrayList<>();

    /** Все рейсы по всем слотам (обратная связь через slot.schedule). */
    @OneToMany(mappedBy = "schedule", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Flight> flights = new ArrayList<>();
}
