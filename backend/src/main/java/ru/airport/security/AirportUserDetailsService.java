package ru.airport.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.airport.model.User;
import ru.airport.repository.UserRepository;

/**
 * Загрузка {@link User} из БД для логина и JWT. В контекст безопасности попадает {@link AirportUserPrincipal}.
 */
@Service
@RequiredArgsConstructor
public class AirportUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User u = userRepository.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new AirportUserPrincipal(u);
    }
}
