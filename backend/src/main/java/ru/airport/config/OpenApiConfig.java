package ru.airport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Документация REST для Swagger UI ({@code /swagger-ui.html}).
 * Мутации и экспорт: {@code POST /api/v1/auth/login}, затем Authorize → Bearer JWT.
 */
@Configuration
public class OpenApiConfig {

    public static final String JWT_BEARER = "jwtBearer";

    @Bean
    public OpenAPI airportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Airport Flight & Schedule API")
                        .description("""
                                REST API `/api/v1`. **GET** (кроме экспорта PDF/XLSX и `/auth/me`) — без авторизации (табло/мобилка, FirstLab).

                                **Запись и экспорт:** роль **DISPATCHER**; `POST /api/v1/auth/login` → **Authorize** → Bearer JWT.

                                **Профиль и выход:** `GET /api/v1/auth/me`, `POST /api/v1/auth/logout` — с валидным Bearer (DISPATCHER и READ_ONLY). После logout тот же токен отклоняется.

                                Ошибки: **404** — сущность не найдена; **409** — конфликт бизнес-правил.""")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(JWT_BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("POST /api/v1/auth/login; для GET /api/v1/auth/me — Bearer accessToken")));
    }
}
