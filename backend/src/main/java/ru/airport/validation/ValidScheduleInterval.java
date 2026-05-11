package ru.airport.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Плановый вылет должен быть строго раньше планового прилёта.
 */
@Documented
@Constraint(validatedBy = ValidScheduleIntervalValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidScheduleInterval {

    String message() default "Плановый вылет должен быть строго раньше планового прилёта";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
