package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.classify-api")
public record ClassifyApiProperties(String username, String password) {

    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
