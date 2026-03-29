package ru.airport.exception;

import lombok.Getter;

/**
 * Сущность не найдена по идентификатору (для последующего маппинга в HTTP 404).
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final Object id;

    public ResourceNotFoundException(String resource, Object id) {
        super("%s not found: %s".formatted(resource, id));
        this.resource = resource;
        this.id = id;
    }
}
