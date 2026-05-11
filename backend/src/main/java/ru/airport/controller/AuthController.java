package ru.airport.controller;

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
import ru.airport.dto.LoginRq;
import ru.airport.dto.LoginRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.security.AirportUserPrincipal;
import ru.airport.service.AuthService;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginRs> login(@Valid @RequestBody LoginRq rq) {
        return ResponseEntity.ok(authService.login(rq));
    }

    /** Инвалидация текущего JWT; повторное использование того же токена отклоняется фильтром. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    /** Профиль текущего пользователя по Bearer JWT. */
    @GetMapping("/me")
    public ResponseEntity<UserProfileRs> me(@AuthenticationPrincipal AirportUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.currentUserProfile(principal));
    }
}
