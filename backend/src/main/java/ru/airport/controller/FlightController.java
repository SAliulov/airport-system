package ru.airport.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.FlightRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.model.FlightStatus;
import ru.airport.service.FlightService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
@Tag(name = "Flights")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Рейс или связанная сущность не найдены. Тело: {\"error\", \"resource\", \"id\"}.")
})
public class FlightController {

    private final FlightService flightService;

    /**
     * Табло FirstLab §2: дата, статус, авиакомпания, направление (IATA аэропорта вылета или прилёта).
     */
    @GetMapping
    public List<FlightRs> list(
            @Parameter(
                    name = "date",
                    description = "Календарный день планового вылета (только дата, формат yyyy-MM-dd, не ISO date-time).",
                    example = "2026-05-01",
                    schema = @Schema(type = "string", format = "date"))
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(
                    name = "status",
                    description = "Статус выполняемого рейса (flight).",
                    schema = @Schema(implementation = FlightStatus.class))
            @RequestParam(name = "status", required = false) FlightStatus status,
            @Parameter(
                    name = "airline",
                    description = "Идентификатор авиакомпании в БД (`airline_id`), см. GET /api/v1/airlines.")
            @RequestParam(name = "airline", required = false) Integer airline,
            @Parameter(
                    name = "direction",
                    description = "IATA аэропорта вылета или прилёта (3 буквы); фильтр по совпадению с origin или destination.")
            @RequestParam(name = "direction", required = false) String direction
    ) {
        return flightService.list(date, status, airline, direction);
    }

    @GetMapping("/{id}")
    public FlightRs get(@PathVariable("id") Integer id) {
        return flightService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlightRs create(@RequestBody @Valid FlightRq rq) {
        return flightService.create(rq);
    }

    @PutMapping("/{id}/status")
    @ApiResponse(
            responseCode = "409",
            description = "Недопустимая смена статуса. Тело: {\"error\"}.")
    public FlightRs updateStatus(@PathVariable("id") Integer id, @RequestBody @Valid FlightStatusUpdateRq rq) {
        return flightService.updateStatus(id, rq);
    }

    @PutMapping("/{id}/aircraft")
    @ApiResponse(
            responseCode = "409",
            description = "Несовместимость типа ВС с гейтом или другое правило. Тело: {\"error\"}.")
    public FlightRs assignAircraft(@PathVariable("id") Integer id, @RequestBody @Valid FlightAircraftAssignmentRq rq) {
        return flightService.assignAircraft(id, rq);
    }

    @PostMapping("/{id}/gate-assignment")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(
            responseCode = "409",
            description = "Гейт занят, неактивен или несовместим с ВС. Тело: {\"error\"}.")
    public GateAssignmentRs assignGate(@PathVariable("id") Integer id, @RequestBody @Valid GateAssignmentRq rq) {
        return flightService.assignGate(id, rq);
    }

    @GetMapping("/{id}/delay-warnings")
    @Operation(summary = "Предупреждения о задержке по рейсу", description = "Вложенный ресурс под `/flights/{id}`; отдельного контроллера нет (см. AGENTS §8).")
    public List<DelayWarningRs> listDelayWarnings(@PathVariable("id") Integer id) {
        return flightService.listDelayWarnings(id);
    }

    @PostMapping("/{id}/delay-warnings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать предупреждение о задержке", description = "DISPATCHER; тот же вложенный путь `/flights/{id}/delay-warnings`.")
    public DelayWarningRs addDelayWarning(@PathVariable("id") Integer id, @RequestBody @Valid DelayWarningRq rq) {
        return flightService.addDelayWarning(id, rq);
    }
}
