package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usuário e senha que o Prometheus usa no {@code /actuator/prometheus} (HTTP Basic). Sem os dois,
 * o endpoint recusa todo mundo.
 */
@ConfigurationProperties("app.metrics")
public record MetricsProperties(String username, String password) {

    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
