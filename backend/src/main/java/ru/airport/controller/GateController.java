package ru.airport.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Gates")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Гейт не найден. Тело: {\"error\", \"resource\", \"id\"}.")
})
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

    /**
     * Данные для timeline (задача 7). Параметр {@code date} — календарный день в локальной зоне сервера.
     */
    @GetMapping("/timeline")
    public List<GateTimelineSegmentRs> timeline(
            @Parameter(
                    description = "Календарный день (только дата, без времени). Не используйте ISO date-time из примера Swagger.",
                    example = "2026-03-30",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(name = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return gateService.getTimelineForDay(date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(
            responseCode = "409",
            description = "Номер гейта уже занят. Тело: {\"error\"}.")
    public GateRs create(@RequestBody @Valid GateRq rq) {
        return gateService.create(rq);
    }

    @PutMapping("/{id}")
    @ApiResponse(
            responseCode = "409",
            description = "Номер гейта уже занят другой записью. Тело: {\"error\"}.")
    public GateRs update(@PathVariable("id") Integer id, @RequestBody @Valid GateRq rq) {
        return gateService.update(id, rq);
    }
}
