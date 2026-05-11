package ru.airport.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory blacklist отозванных JWT (logout). Отдельный bean, чтобы не замыкать
 * {@code JwtAuthenticationFilter} → {@code AuthService} → {@code AuthenticationManager} → Security.
 * Хранит exp из claims; просроченные записи удаляются по расписанию и при проверке {@link #contains}.
 */
@Component
public class JwtTokenBlacklist {

    private final JwtTokenUtil jwtTokenUtil;
    private final ConcurrentHashMap<String, Instant> digestToExpiry = new ConcurrentHashMap<>();

    public JwtTokenBlacklist(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    public void add(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String trimmed = rawToken.trim();
        digestToExpiry.put(digest(trimmed), jwtTokenUtil.extractExpiration(trimmed));
    }

    public boolean contains(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        String d = digest(rawToken.trim());
        Instant exp = digestToExpiry.get(d);
        if (exp == null) {
            return false;
        }
        if (exp.isBefore(Instant.now())) {
            digestToExpiry.remove(d, exp);
            return false;
        }
        return true;
    }

    @Scheduled(fixedRate = 60_000)
    public void purgeExpired() {
        Instant now = Instant.now();
        digestToExpiry.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }

    private static String digest(String token) {
        return DigestUtils.md5DigestAsHex(token.getBytes(StandardCharsets.UTF_8));
    }
}
