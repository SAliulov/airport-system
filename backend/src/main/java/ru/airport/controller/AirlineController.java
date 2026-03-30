package ru.airport.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.AirlineRs;
import ru.airport.service.AirlineService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/airlines")
@RequiredArgsConstructor
@Tag(name = "Airlines")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Авиакомпания не найдена. Тело: {\"error\", \"resource\", \"id\"}.")
})
public class AirlineController {

    private final AirlineService airlineService;

    @GetMapping
    public List<AirlineRs> list() {
        return airlineService.findAll();
    }

    @GetMapping("/{id}")
    public AirlineRs get(@PathVariable("id") Integer id) {
        return airlineService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(
            responseCode = "409",
            description = "IATA-код уже занят. Тело: {\"error\"}.")
    public AirlineRs create(@RequestBody @Valid AirlineRq rq) {
        return airlineService.create(rq);
    }

    @PutMapping("/{id}")
    @ApiResponse(
            responseCode = "409",
            description = "IATA-код уже занят другой записью. Тело: {\"error\"}.")
    public AirlineRs update(@PathVariable("id") Integer id, @RequestBody @Valid AirlineRq rq) {
        return airlineService.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        airlineService.delete(id);
    }
}
