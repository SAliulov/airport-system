package ru.airport.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.airport.model.FlightStatus;
import ru.airport.service.ScheduleExportService;
import ru.airport.service.ScheduleService;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedules")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Расписание не найдено. Тело: {\"error\", \"resource\", \"id\"}.")
})
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleExportService scheduleExportService;

    /**
     * Фильтры: дата (плановый вылет в этот день), авиакомпания, статус рейсов в этот день,
     * поиск по номеру рейса, направление (IATA вылета или прилёта) — FirstLab §2, задача 2.
     */
    @GetMapping
    public List<ScheduleRs> list(
            @Parameter(
                    name = "date",
                    description = "День планового вылета (yyyy-MM-dd).",
                    example = "2026-05-01",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(
                    name = "airline",
                    description = "Идентификатор авиакомпании (`airline_id`), см. GET /api/v1/airlines.")
            @RequestParam(name = "airline", required = false) Integer airline,
            @Parameter(
                    name = "status",
                    description = "Фильтр по статусу выполняемых рейсов (flight) в календарный день `date`; не поле шаблона расписания. В `ScheduleRs` статус рейса не возвращается — смотрите сущность flight / список рейсов.",
                    schema = @Schema(implementation = FlightStatus.class))
            @RequestParam(name = "status", required = false) FlightStatus status,
            @Parameter(name = "search", description = "Подстрока в номере рейса (без учёта регистра).")
            @RequestParam(name = "search", required = false) String search,
            @Parameter(
                    name = "direction",
                    description = "IATA аэропорта вылета или прилёта; фильтр по origin или destination.")
            @RequestParam(name = "direction", required = false) String direction
    ) {
        return scheduleService.list(date, airline, status, search, direction);
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
