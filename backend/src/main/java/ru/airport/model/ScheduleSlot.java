package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Слот шаблона: день недели (WEEKLY) или только время (INTERVAL) + часы вылета/прилёта.
 */
@Entity
@Table(name = "schedule_slot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"schedule", "flights"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    @EqualsAndHashCode.Include
    private Integer slotId;

    /** ISO: 1=Пн … 7=Вс; NULL для INTERVAL. */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @OneToMany(mappedBy = "slot", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Flight> flights = new ArrayList<>();
}
