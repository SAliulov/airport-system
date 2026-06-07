package ru.airport.mapper;

import org.springframework.stereotype.Component;
import ru.airport.dto.UserProfileRs;
import ru.airport.security.AirportUserPrincipal;

/** Entity ↔ DTO для профиля пользователя. */
@Component
public class UserDtoMapper {

    public UserProfileRs toUserProfileRs(AirportUserPrincipal principal) {
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("");
        return UserProfileRs.builder()
                .userId(principal.getUserId())
                .username(principal.getUsername())
                .role(role)
                .build();
    }
}
