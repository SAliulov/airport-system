package ru.airport.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import ru.airport.dto.ScheduleRq;

import java.time.LocalDateTime;

public class ValidScheduleIntervalValidator implements ConstraintValidator<ValidScheduleInterval, ScheduleRq> {

    @Override
    public boolean isValid(ScheduleRq rq, ConstraintValidatorContext context) {
        if (rq == null) {
            return true;
        }
        LocalDateTime dep = rq.getScheduledDeparture();
        LocalDateTime arr = rq.getScheduledArrival();
        if (dep == null || arr == null) {
            return true;
        }
        return dep.isBefore(arr);
    }
}
