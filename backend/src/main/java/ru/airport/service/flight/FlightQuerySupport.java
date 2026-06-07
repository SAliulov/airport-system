package ru.airport.service.flight;

import ru.airport.model.Flight;
import ru.airport.model.FlightStatus;
import ru.airport.model.Schedule;

/**
 * Pure helpers для фильтрации и нормализации query-параметров списка рейсов.
 * Дублирует UX-фильтры клиента; источник истины для статусов — {@link ru.airport.validation.FlightStatusParser}.
 */
public final class FlightQuerySupport {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private FlightQuerySupport() {
    }

    public static String normalizeSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    public static String normalizeAirport(String direction) {
        if (direction == null || direction.isBlank()) {
            return null;
        }
        return direction.trim().toUpperCase();
    }

    public static boolean matchesAirportDirection(Flight flight, String airportIataUpper) {
        Schedule s = flight.getSchedule();
        if (s == null) {
            return false;
        }
        String o = trimUpper(s.getOriginAirport());
        String d = trimUpper(s.getDestinationAirport());
        return airportIataUpper.equals(o) || airportIataUpper.equals(d);
    }

    public static String trimUpper(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    public static int resolvePageIndex(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    public static int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public static void touchCollections(Flight f) {
        if (f.getGateAssignments() != null) {
            f.getGateAssignments().size();
        }
        if (f.getDelayWarnings() != null) {
            f.getDelayWarnings().size();
        }
    }
}
