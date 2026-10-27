package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Usuário e senha que a API usa em {@code POST /api/classify} (HTTP Basic), para conferir a foto antes de aceitar
 * o relato. Sem os dois, a rota recusa todo mundo e a API segue só com a classificação pela fila.
 */
@ConfigurationProperties("app.classify-api")
public record ClassifyApiProperties(String username, String password) {

    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
