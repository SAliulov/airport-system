package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Справочник авиакомпаний.
 * Кэшируется в Spring Cache (справочник меняется редко).
 *
 * GRASP: Information Expert — знает всё об авиакомпании.
 */
@Entity
@Table(name = "airline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "schedules")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "airline_id")
    @EqualsAndHashCode.Include
    private Integer airlineId;

    /**
     * Двухсимвольный IATA-код авиакомпании (SU, U6, BA).
     * Уникален, не может быть null.
     */
    @Column(name = "iata_code", columnDefinition = "char(2)",
            nullable = false, unique = true)
    private String iataCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "country", length = 77)
    private String country;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * ON DELETE RESTRICT — нельзя удалить авиакомпанию, пока у неё есть расписания.
     * mappedBy указывает на поле владеющей стороны в Schedule.
     */
    @OneToMany(mappedBy = "airline", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Schedule> schedules = new ArrayList<>();
}