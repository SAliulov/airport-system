package ru.airport;

import org.junit.jupiter.api.Test;

/**
 * Минимальный тест, чтобы {@code ./gradlew test} не был пустым, когда IT с Testcontainers отключены (нет Docker).
 */
class SmokeTest {

    @Test
    void loads() {
    }
}
