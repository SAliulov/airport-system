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
import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.service.AircraftTypeService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/aircraft-types")
@RequiredArgsConstructor
@Tag(name = "Aircraft types")
@ApiResponses({
        @ApiResponse(
                responseCode = "404",
                description = "Тип ВС не найден. Тело: {\"error\", \"resource\", \"id\"}.")
})
public class AircraftTypeController {

    private final AircraftTypeService aircraftTypeService;

    @GetMapping
    public List<AircraftTypeRs> list() {
        return aircraftTypeService.findAll();
    }

    @GetMapping("/{id}")
    public AircraftTypeRs get(@PathVariable("id") Integer id) {
        return aircraftTypeService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponse(
            responseCode = "409",
            description = "ICAO-код типа ВС уже занят. Тело: {\"error\"}.")
    public AircraftTypeRs create(@RequestBody @Valid AircraftTypeRq rq) {
        return aircraftTypeService.create(rq);
    }

    @PutMapping("/{id}")
    @ApiResponse(
            responseCode = "409",
            description = "ICAO-код уже занят другой записью. Тело: {\"error\"}.")
    public AircraftTypeRs update(@PathVariable("id") Integer id, @RequestBody @Valid AircraftTypeRq rq) {
        return aircraftTypeService.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        aircraftTypeService.delete(id);
    }
}
