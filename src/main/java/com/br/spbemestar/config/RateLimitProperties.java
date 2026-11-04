package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100000") int maxTrackedClients,
        @DefaultValue List<Rule> rules
) {

    public record Rule(String name, String method, String path, int capacity, Duration period) {
    }
}
