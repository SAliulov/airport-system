package ru.airport.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Текущий пользователь по JWT (без пароля). REST: {@code GET /api/v1/auth/me}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileRs {

    private Integer userId;
    private String username;
    /** Роль без префикса ROLE_ */
    private String role;
}
