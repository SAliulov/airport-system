package ru.airport.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import ru.airport.dto.ScheduleRq;

public class DifferentAirportsValidator implements ConstraintValidator<DifferentAirports, ScheduleRq> {

    @Override
    public boolean isValid(ScheduleRq rq, ConstraintValidatorContext context) {
        if (rq == null) {
            return true;
        }
        String o = rq.getOriginAirport();
        String d = rq.getDestinationAirport();
        if (o == null || d == null || o.isBlank() || d.isBlank()) {
            return true;
        }
        return !o.trim().equalsIgnoreCase(d.trim());
    }
}
