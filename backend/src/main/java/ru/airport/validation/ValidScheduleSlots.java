package ru.airport.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Базовая проверка слотов: непустой список, overnight допустим (arrival &lt;= departure).
 */
@Documented
@Constraint(validatedBy = ValidScheduleSlotsValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidScheduleSlots {

    String message() default "Некорректные слоты расписания";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
