package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.classification.precheck")
public record PhotoPrecheckProperties(
        String url,
        String username,
        String password,
        @DefaultValue("3s") Duration timeout,
        @DefaultValue("0.8") double rejectConfidence
) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
