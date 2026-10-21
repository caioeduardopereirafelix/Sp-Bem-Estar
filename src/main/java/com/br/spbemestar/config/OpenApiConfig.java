package com.br.spbemestar.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearer";

    // Botão "Authorize" do Swagger UI: cole o accessToken de POST /api/v1/auth/login
    @Bean
    OpenAPI openApi() {
        return new OpenAPI().components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
