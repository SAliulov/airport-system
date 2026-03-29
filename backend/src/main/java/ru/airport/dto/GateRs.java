package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.SizeCategory;

/**
 * Ответ REST: гейт.
 * Задача 4; {@code GET /api/v1/gates}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateRs {

    private Integer gateId;
    private String gateNumber;
    private String terminal;
    private Boolean isActive;
    private SizeCategory maxSizeCategory;
}
