package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.SizeCategory;

/**
 * Ответ REST: тип воздушного судна.
 * Задача 1; {@code GET /api/v1/aircraft-types}; также вложение в {@link FlightRs} (задачи 2, 5).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AircraftTypeRs {

    private Integer aircraftTypeId;
    private String icaoCode;
    private Integer passengerCapacity;
    private SizeCategory sizeCategory;
}