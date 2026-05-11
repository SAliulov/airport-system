package ru.airport.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Аэропорт вылета и прилёта не должны совпадать.
 */
@Documented
@Constraint(validatedBy = DifferentAirportsValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface DifferentAirports {

    String message() default "Аэропорт вылета и прилёта должны различаться";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
