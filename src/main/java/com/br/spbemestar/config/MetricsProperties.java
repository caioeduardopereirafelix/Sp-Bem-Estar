package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.metrics")
public record MetricsProperties(String username, String password) {

    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
