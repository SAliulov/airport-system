package ru.airport.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleRs;
import ru.airport.service.ScheduleExportService;
import ru.airport.service.ScheduleService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleExportService scheduleExportService;

    @GetMapping
    public List<ScheduleRs> listAll() {
        return scheduleService.listAll();
    }

    @GetMapping("/filter")
    public List<ScheduleRs> filter(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "airline", required = false) Integer airline,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "direction", required = false) String direction
    ) {
        return scheduleService.filter(date, airline, status, direction);
    }

    @GetMapping("/search")
    public List<ScheduleRs> search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "airline", required = false) Integer airline,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "direction", required = false) String direction
    ) {
        return scheduleService.search(query, date, airline, status, direction);
    }

    @GetMapping("/{id}")
    public ScheduleRs get(@PathVariable("id") Integer id) {
        return scheduleService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleRs create(@RequestBody @Valid ScheduleRq rq) {
        return scheduleService.create(rq);
    }

    @PutMapping("/{id}")
    public ScheduleRs update(@PathVariable("id") Integer id, @RequestBody @Valid ScheduleRq rq) {
        return scheduleService.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        scheduleService.delete(id);
    }

    @GetMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        byte[] body = scheduleExportService.exportPdf(date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule-%s.pdf\"".formatted(date))
                .body(body);
    }

    @GetMapping(value = "/export/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) throws IOException {
        byte[] body = scheduleExportService.exportExcel(date);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"schedule-%s.xlsx\"".formatted(date))
                .body(body);
    }
}
