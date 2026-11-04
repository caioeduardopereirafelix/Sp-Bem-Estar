package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.reporter")
public record ReporterProperties(
        String hashSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("90d") Duration refreshTokenTtl,
        @DefaultValue("spbemestar-reporter") String audience
) {
}
