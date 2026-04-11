package ru.airport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(example = "1")
    private Integer userId;

    @Schema(example = "dispatcher")
    private String username;

    @Schema(example = "DISPATCHER", description = "Роль без префикса ROLE_")
    private String role;
}
