package ru.airport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.dto.OperationalEventRs;
import ru.airport.dto.PageRs;
import ru.airport.service.OperationalEventService;

@RestController
@RequestMapping("/api/v1/operational-events")
@RequiredArgsConstructor
public class OperationalEventController {

    private final OperationalEventService operationalEventService;

    @GetMapping
    public PageRs<OperationalEventRs> list(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return operationalEventService.list(page, size);
    }
}
