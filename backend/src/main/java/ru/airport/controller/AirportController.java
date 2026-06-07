package ru.airport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.config.AirportProperties;
import ru.airport.dto.AirportRs;

@RestController
@RequestMapping("/api/v1/airport")
@RequiredArgsConstructor
public class AirportController {

    private final AirportProperties airportProperties;

    @GetMapping
    public AirportRs getAirportConfig() {
        return AirportRs.builder()
                .homeIata(airportProperties.getHomeIata())
                .timezone(airportProperties.getTimezone())
                .gatePlanWindowHours(airportProperties.getGatePlanWindowHours())
                .gatePostGraceMinutes(airportProperties.getGatePostGraceMinutes())
                .build();
    }
}
