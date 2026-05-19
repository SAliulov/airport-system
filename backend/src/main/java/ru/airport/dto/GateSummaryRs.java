package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.airport.model.SizeCategory;

/**
 * Краткое представление гейта для вложения в другие ответы.
 * Задачи 4, 7; используется внутри {@link GateAssignmentRs}, таймлайна.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GateSummaryRs {

    private Integer gateId;
    private String gateNumber;
    private String terminal;
    /** Вместимость гейта по категории размера ВС (из справочника gate). */
    private SizeCategory maxSizeCategory;
}
