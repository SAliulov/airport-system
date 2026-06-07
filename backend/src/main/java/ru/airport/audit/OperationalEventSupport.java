package ru.airport.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import ru.airport.model.FlightStatus;

/** Общие утилиты для operational-event mapper. */
final class OperationalEventSupport {

    private OperationalEventSupport() {
    }

    static boolean isDispatcher(Authentication authentication, String username) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        if ("anonymous".equalsIgnoreCase(username) || "anonymousUser".equalsIgnoreCase(username)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_DISPATCHER"::equals);
    }

    static Integer firstIntegerArg(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof Integer id) {
                return id;
            }
        }
        return null;
    }

    static String trimDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String trimmed = details.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String statusLabel(FlightStatus status) {
        if (status == null) {
            return "?";
        }
        return switch (status) {
            case SCHEDULED -> "Запланирован";
            case DEPARTED -> "Вылетел";
            case ARRIVED -> "Прибыл";
            case DELAYED -> "Задержан";
            case CANCELLED -> "Отменён";
        };
    }

    record FlightContext(Integer flightId, String flightNumber) {
    }
}
