package ru.airport.dto;

import lombok.Builder;
import lombok.Value;

/** Публичная конфигурация домашнего аэропорта для клиентов (источник: {@code airport.*} в application.yml). */
@Value
@Builder
public class AirportRs {
    String homeIata;
    String timezone;
    /** Окно ±N часов вокруг планового вылета/прилёта для интервала гейта. */
    int gatePlanWindowHours;
    /** Минуты после assigned_to для допуска фактического времени у гейта. */
    int gatePostGraceMinutes;
}
