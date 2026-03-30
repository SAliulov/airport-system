package ru.airport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Документация REST для Swagger UI ({@code /swagger-ui.html}).
 * Для POST/PUT/DELETE и экспорта нажмите «Authorize» и введите HTTP Basic: {@code dispatcher} / {@code dispatcher123}.
 */
@Configuration
public class OpenApiConfig {

    public static final String BASIC_AUTH = "dispatcherBasic";

    @Bean
    public OpenAPI airportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Airport Flight & Schedule API")
                        .description("""
                                REST API `/api/v1`. GET-запросы читаются без авторизации; запись и экспорт — HTTP Basic (роль DISPATCHER).

                                Ошибки: **404** — сущность не найдена, тело `{"error","resource","id"}`. **409** — конфликт бизнес-правил, тело `{"error"}`.""")
                        .version("1.0"))
                .components(new Components()
                        .addSecuritySchemes(BASIC_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Учётная запись диспетчера (см. SecurityConfig)")));
    }
}
