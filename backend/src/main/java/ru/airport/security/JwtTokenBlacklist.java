package ru.airport.security;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blacklist отозванных JWT (logout). Отдельный bean, чтобы не замыкать
 * {@code JwtAuthenticationFilter} → {@code AuthService} → {@code AuthenticationManager} → Security.
 */
@Component
public class JwtTokenBlacklist {

    private final Set<String> digests = ConcurrentHashMap.newKeySet();

    public void add(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        digests.add(digest(rawToken.trim()));
    }

    public boolean contains(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        return digests.contains(digest(rawToken.trim()));
    }

    private static String digest(String token) {
        return DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
    }
}
