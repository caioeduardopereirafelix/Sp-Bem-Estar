package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("app.login")
public record LoginProperties(
        @DefaultValue("10m") Duration codeTtl,
        @DefaultValue("60s") Duration resendInterval,
        @DefaultValue("5") int maxCodesPerHour,
        @DefaultValue("5") int maxAttempts
) {
}
