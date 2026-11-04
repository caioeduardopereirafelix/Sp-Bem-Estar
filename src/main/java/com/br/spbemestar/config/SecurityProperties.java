package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("8h") Duration tokenTtl,
        @DefaultValue("spbemestar-api") String issuer,
        @DefaultValue BootstrapOperator bootstrapOperator,
        @DefaultValue BootstrapOperator bootstrapCeo
) {

    public record BootstrapOperator(String email, @DefaultValue("Operador") String name) {

        public boolean enabled() {
            return email != null && !email.isBlank();
        }
    }
}
