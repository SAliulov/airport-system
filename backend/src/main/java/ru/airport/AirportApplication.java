package ru.airport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * {@code @EnableTransactionManagement(order = HIGHEST_PRECEDENCE)} заставляет транзакционный
 * адвайзер оборачивать вызовы СНАРУЖИ {@link ru.airport.aspect.ServiceAuditAspect} (там {@code @Order(0)}) —
 * иначе аспект успевает опубликовать operational-event ПОСЛЕ коммита транзакции целевого метода,
 * и {@code @TransactionalEventListener(phase = AFTER_COMMIT)} в WebSocketEventListener молча
 * отбрасывает событие (нет активной синхронизации транзакции на момент publishEvent).
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class AirportApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirportApplication.class, args);
    }
}