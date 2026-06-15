package ru.airport.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.airport.security.JwtAuthenticationFilter;

import java.util.List;

/**
 * JWT (Bearer) для мутаций и экспорта; пользователи в таблице {@code app_user}.
 * GET под {@code /api/v1/**} (кроме экспорта) — без токена (табло / мобилка, AGENTS §9).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AirportProperties airportProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, AirportProperties airportProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.airportProperties = airportProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules/export/pdf").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules/export/excel").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/flights", "/api/v1/flights/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/flights/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/flights/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/schedules", "/api/v1/schedules/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/schedules/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/schedules/**").hasRole("DISPATCHER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").permitAll()
                        .requestMatchers("/api/v1/**").hasRole("DISPATCHER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowCredentials(true);
        c.setAllowedOriginPatterns(airportProperties.getAllowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        source.registerCorsConfiguration("/ws/**", c);
        return source;
    }
}
