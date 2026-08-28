package ru.airport.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.service.DelayWarningService;

import java.time.LocalDate;
import java.util.List;

/**
 * Сводный список и управление предупреждениями о задержках по всем рейсам (вкладка «Задержки»).
 * Создание предупреждения по-прежнему через {@code POST /flights/{id}/delay-warnings}
 * ({@link FlightController}) — привязано к одному рейсу и требует статус DELAYED.
 */
@RestController
@RequestMapping("/api/v1/delay-warnings")
@RequiredArgsConstructor
public class DelayWarningController {

    private final DelayWarningService delayWarningService;

    @GetMapping
    public List<DelayWarningRs> list(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "airline", required = false) Integer airline,
            @RequestParam(name = "query", required = false) String query
    ) {
        return delayWarningService.list(date, airline, query);
    }

    @PutMapping("/{id}")
    public DelayWarningRs update(@PathVariable("id") Integer id, @RequestBody @Valid DelayWarningRq rq) {
        return delayWarningService.updateDelayWarning(id, rq);
    }

    @DeleteMapping("/{id}")
    public DelayWarningRs delete(@PathVariable("id") Integer id) {
        return delayWarningService.deleteDelayWarning(id);
    }
}
