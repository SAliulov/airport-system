package ru.airport.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Справочник типов воздушных судов.
 * Кэшируется в Spring Cache (справочник меняется редко).
 *
 * size_category используется при проверке совместимости ВС с гейтом
 * (задачи 4 и 5): широкофюзеляжный самолёт не встанет в гейт для узкофюзеляжных.
 */
@Entity
@Table(name = "aircraft_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "flights")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AircraftType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "aircraft_type_id")
    @EqualsAndHashCode.Include
    private Integer aircraftTypeId;

    /**
     * Стандартный ICAO-идентификатор типа ВС (A320, B738, AN24).
     * Уникален, не более 4 символов.
     */
    @Column(name = "icao_code", length = 4, nullable = false, unique = true)
    private String icaoCode;

    @Column(name = "passenger_capacity")
    private Integer passengerCapacity;

    /**
     * Категория размера: NARROW / WIDE / JUMBO.
     * Хранится как строка в БД (VARCHAR(10)), что позволяет
     * добавлять новые категории без миграции схемы.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "size_category", length = 10)
    private SizeCategory sizeCategory;

    // ───── Связи ──────────────────────────────────────────────────────────

    /**
     * ON DELETE SET NULL — рейсы остаются, aircraft_type_id становится NULL.
     */
    @OneToMany(mappedBy = "aircraftType", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Flight> flights = new ArrayList<>();
}