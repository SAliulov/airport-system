package ru.airport.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtTokenUtil {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;

    /**
     * Тело JWT из {@code Authorization: Bearer …}.
     */
    public static Optional<String> resolveBearerToken(HttpServletRequest request) {
        String h = request.getHeader(AUTH_HEADER);
        if (!StringUtils.hasText(h) || !h.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        String t = h.substring(TOKEN_PREFIX.length()).trim();
        return StringUtils.hasText(t) ? Optional.of(t) : Optional.empty();
    }

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + jwtProperties.getExpiration());
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey())
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Момент истечения токена (exp). Невалидный JWT или отсутствующий exp → now + {@code jwt.expiration},
     * чтобы запись blacklist не жила бесконечно.
     */
    public Instant extractExpiration(String token) {
        try {
            Date exp = parseClaims(token).getExpiration();
            if (exp != null) {
                return exp.toInstant();
            }
        } catch (RuntimeException ignored) {
            // неверная подпись / формат
        }
        return Instant.now().plusMillis(jwtProperties.getExpiration());
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        String subject = extractUsername(token);
        if (!subject.equalsIgnoreCase(userDetails.getUsername())) {
            return false;
        }
        try {
            Date exp = parseClaims(token).getExpiration();
            return exp != null && exp.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
