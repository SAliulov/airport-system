package ru.airport.testsupport;

import org.testcontainers.DockerClientFactory;

/**
 * Условие для JUnit 5 {@code @EnabledIf} — интеграционные тесты с Testcontainers.
 */
public final class DockerConditions {

    private DockerConditions() {
    }

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
