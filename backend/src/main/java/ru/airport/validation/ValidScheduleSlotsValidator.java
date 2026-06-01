package ru.airport.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import ru.airport.dto.ScheduleRq;
import ru.airport.dto.ScheduleSlotRq;

public class ValidScheduleSlotsValidator implements ConstraintValidator<ValidScheduleSlots, ScheduleRq> {

    @Override
    public boolean isValid(ScheduleRq rq, ConstraintValidatorContext context) {
        if (rq == null || rq.getSlots() == null || rq.getSlots().isEmpty()) {
            return true;
        }
        for (ScheduleSlotRq slot : rq.getSlots()) {
            if (slot.getDepartureTime() == null || slot.getArrivalTime() == null) {
                continue;
            }
            if (slot.getDepartureTime().equals(slot.getArrivalTime())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                                "Время вылета и прилёта слота не могут совпадать")
                        .addPropertyNode("slots")
                        .addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
