package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.dto.AircraftTypeRq;
import ru.airport.dto.AircraftTypeRs;
import ru.airport.dto.AirlineRq;
import ru.airport.dto.AirlineRs;
import ru.airport.model.AircraftType;
import ru.airport.model.Airline;

/** Entity ↔ DTO для справочников airline и aircraft_type. */
@Component
public class DirectoryDtoMapper {

    public AirlineRs toAirlineRs(Airline a) {
        if (a == null) {
            return null;
        }
        return AirlineRs.builder()
                .airlineId(a.getAirlineId())
                .iataCode(trimIata(a.getIataCode()))
                .name(a.getName())
                .country(a.getCountry())
                .build();
    }

    public Airline newAirline(AirlineRq rq) {
        return Airline.builder()
                .iataCode(rq.getIataCode())
                .name(rq.getName())
                .country(rq.getCountry())
                .build();
    }

    public void apply(AirlineRq rq, Airline a) {
        a.setIataCode(rq.getIataCode());
        a.setName(rq.getName());
        a.setCountry(rq.getCountry());
    }

    public AircraftTypeRs toAircraftTypeRs(AircraftType t) {
        if (t == null) {
            return null;
        }
        return AircraftTypeRs.builder()
                .aircraftTypeId(t.getAircraftTypeId())
                .icaoCode(t.getIcaoCode())
                .passengerCapacity(t.getPassengerCapacity())
                .sizeCategory(t.getSizeCategory())
                .build();
    }

    public AircraftType newAircraftType(AircraftTypeRq rq) {
        return AircraftType.builder()
                .icaoCode(rq.getIcaoCode())
                .passengerCapacity(rq.getPassengerCapacity())
                .sizeCategory(rq.getSizeCategory())
                .build();
    }

    public void apply(AircraftTypeRq rq, AircraftType t) {
        t.setIcaoCode(rq.getIcaoCode());
        t.setPassengerCapacity(rq.getPassengerCapacity());
        t.setSizeCategory(rq.getSizeCategory());
    }

    private static String trimIata(String code) {
        return code == null ? null : code.trim();
    }
}
