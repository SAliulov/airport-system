package ru.airport.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import ru.airport.dto.LoginRq;
import ru.airport.dto.LoginRs;
import ru.airport.dto.UserProfileRs;
import ru.airport.exception.BadRequestException;
import ru.airport.exception.ServerErrorException;
import ru.airport.mapper.DtoMapper;
import ru.airport.security.AirportUserPrincipal;
import ru.airport.security.JwtTokenBlacklist;
import ru.airport.security.JwtTokenUtil;

/**
 * Аутентификация, выдача JWT и инвалидация токена при logout (in-memory blacklist по MD5, как в учебном примере).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtTokenBlacklist jwtTokenBlacklist;
    private final DtoMapper dtoMapper;

    public UserProfileRs currentUserProfile(AirportUserPrincipal principal) {
        return dtoMapper.toUserProfileRs(principal);
    }

    public LoginRs login(LoginRq rq) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(rq.getUsername(), rq.getPassword()));
        Object principalObj = auth.getPrincipal();
        if (!(principalObj instanceof UserDetails principal)) {
            throw new ServerErrorException("Unexpected principal type");
        }
        String token = jwtTokenUtil.generateToken(principal);
        String role = principal.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replace("ROLE_", ""))
                .orElse("");
        return LoginRs.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .role(role)
                .build();
    }

    /**
     * Добавляет текущий Bearer-токен в blacklist; последующие запросы с ним не получат аутентификацию.
     */
    public void logout(HttpServletRequest request) {
        String token = JwtTokenUtil.resolveBearerToken(request)
                .orElseThrow(() -> new BadRequestException(
                        "Нужен заголовок Authorization: Bearer <accessToken>"));
        jwtTokenBlacklist.add(token);
    }
}
