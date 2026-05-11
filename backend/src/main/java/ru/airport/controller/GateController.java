package ru.airport.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.dto.GateRq;
import ru.airport.dto.GateRs;
import ru.airport.dto.GateTimelineSegmentRs;
import ru.airport.service.GateService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    @GetMapping
    public List<GateRs> list() {
        return gateService.findAll();
    }

    @GetMapping("/{id}")
    public GateRs get(@PathVariable("id") Integer id) {
        return gateService.getById(id);
    }

    /** Данные для timeline (задача 7). Параметр {@code date} — календарный день в локальной зоне сервера. */
    @GetMapping("/timeline")
    public List<GateTimelineSegmentRs> timeline(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return gateService.getTimelineForDay(date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GateRs create(@RequestBody @Valid GateRq rq) {
        return gateService.create(rq);
    }

    @PutMapping("/{id}")
    public GateRs update(@PathVariable("id") Integer id, @RequestBody @Valid GateRq rq) {
        return gateService.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        gateService.delete(id);
    }
}
