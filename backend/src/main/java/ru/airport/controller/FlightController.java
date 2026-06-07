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
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.DelayWarningRq;
import ru.airport.dto.DelayWarningRs;
import ru.airport.dto.FlightActualTimesRq;
import ru.airport.dto.FlightAircraftAssignmentRq;
import ru.airport.dto.GateRs;
import ru.airport.dto.FlightGenerateRq;
import ru.airport.dto.FlightGenerateRs;
import ru.airport.dto.FlightRq;
import ru.airport.dto.FlightRs;
import ru.airport.dto.FlightStatusUpdateRq;
import ru.airport.dto.GateAssignmentRq;
import ru.airport.dto.GateAssignmentRs;
import ru.airport.dto.PageRs;
import ru.airport.service.FlightService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @GetMapping
    public PageRs<FlightRs> listAll(
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        return flightService.listAll(page, size, sort);
    }

    @GetMapping("/filter")
    public PageRs<FlightRs> filter(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "airline", required = false) Integer airline,
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "terminal", required = false) String terminal,
            @RequestParam(name = "hourFrom", required = false) Integer hourFrom,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        return flightService.filter(date, status, airline, direction, origin, destination,
                terminal, hourFrom, page, size, sort);
    }

    @GetMapping("/search")
    public PageRs<FlightRs> search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "airline", required = false) Integer airline,
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "destination", required = false) String destination,
            @RequestParam(name = "terminal", required = false) String terminal,
            @RequestParam(name = "hourFrom", required = false) Integer hourFrom,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "sort", required = false) String sort
    ) {
        return flightService.search(query, date, status, airline, direction, origin, destination,
                terminal, hourFrom, page, size, sort);
    }

    @GetMapping("/{id}")
    public FlightRs get(@PathVariable("id") Integer id) {
        return flightService.getById(id);
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public FlightGenerateRs generate(@RequestBody @Valid FlightGenerateRq rq) {
        return flightService.generate(rq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FlightRs create(@RequestBody @Valid FlightRq rq) {
        return flightService.create(rq);
    }

    @PutMapping("/{id}")
    public FlightRs update(@PathVariable("id") Integer id, @RequestBody @Valid FlightRq rq) {
        return flightService.update(id, rq);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") Integer id) {
        flightService.delete(id);
    }

    @PutMapping("/{id}/status")
    public FlightRs updateStatus(@PathVariable("id") Integer id, @RequestBody @Valid FlightStatusUpdateRq rq) {
        return flightService.updateStatus(id, rq);
    }

    @PutMapping("/{id}/actual-times")
    public FlightRs correctActualTimes(@PathVariable("id") Integer id, @RequestBody FlightActualTimesRq rq) {
        return flightService.correctActualTimes(id, rq);
    }

    @PutMapping("/{id}/aircraft")
    public FlightRs assignAircraft(@PathVariable("id") Integer id, @RequestBody @Valid FlightAircraftAssignmentRq rq) {
        return flightService.assignAircraft(id, rq);
    }

    @PostMapping("/{id}/gate-assignment")
    @ResponseStatus(HttpStatus.CREATED)
    public GateAssignmentRs assignGate(@PathVariable("id") Integer id, @RequestBody @Valid GateAssignmentRq rq) {
        return flightService.assignGate(id, rq);
    }

    @GetMapping("/{id}/available-gates")
    public List<GateRs> listAvailableGates(@PathVariable("id") Integer id) {
        return flightService.listAvailableGates(id);
    }

    @GetMapping("/{id}/compatible-aircraft-types")
    public List<AircraftTypeRs> listCompatibleAircraftTypes(@PathVariable("id") Integer id) {
        return flightService.listCompatibleAircraftTypes(id);
    }

    @GetMapping("/{id}/delay-warnings")
    public List<DelayWarningRs> listDelayWarnings(@PathVariable("id") Integer id) {
        return flightService.listDelayWarnings(id);
    }

    @PostMapping("/{id}/delay-warnings")
    @ResponseStatus(HttpStatus.CREATED)
    public DelayWarningRs addDelayWarning(@PathVariable("id") Integer id, @RequestBody @Valid DelayWarningRq rq) {
        return flightService.addDelayWarning(id, rq);
    }
}
