package ru.airport.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Секрет и TTL JWT (application.yml, префикс {@code jwt.}).
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * Строка-секрет; перед подписью нормализуется через SHA-256 до 256 бит.
     */
    private String secret = "";

    /**
     * Время жизни access-токена, миллисекунды.
     */
    private long expiration = 86400000L;
}
