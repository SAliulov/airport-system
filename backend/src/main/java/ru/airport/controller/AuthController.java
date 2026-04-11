package ru.airport.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.airport.config.OpenApiConfig;
import ru.airport.dto.LoginRq;
import ru.airport.dto.LoginRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.security.AirportUserPrincipal;
import ru.airport.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginRs> login(@Valid @RequestBody LoginRq rq) {
        return ResponseEntity.ok(authService.login(rq));
    }

    /**
     * Инвалидация текущего JWT (клиент удаляет токен локально). Повторное использование того же токена не пройдёт фильтр.
     */
    @PostMapping("/logout")
    @Operation(summary = "Выход", description = "Тот же Bearer, что для защищённых методов; роли DISPATCHER и READ_ONLY")
    @SecurityRequirement(name = OpenApiConfig.JWT_BEARER)
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Профиль по Bearer JWT (FirstLab: разграничение доступа; клиент диспетчера после логина).
     */
    @GetMapping("/me")
    @Operation(summary = "Текущий пользователь", description = "Нужен заголовок Authorization: Bearer &lt;accessToken&gt;")
    @SecurityRequirement(name = OpenApiConfig.JWT_BEARER)
    public ResponseEntity<UserProfileRs> me(@AuthenticationPrincipal AirportUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
        return ResponseEntity.ok(UserProfileRs.builder()
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .role(role)
                .build());
    }
}
