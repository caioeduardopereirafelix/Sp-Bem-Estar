package com.br.spbemestar.observability;

import com.br.spbemestar.service.cache.JsonCache;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("cache")
@RequiredArgsConstructor
public class CacheHealthIndicator implements HealthIndicator {

    private final JsonCache cache;

    @Override
    public Health health() {
        return cache.ping() ? Health.up().build()
                : Health.status(BacklogMonitor.DEGRADED).withDetail("redis", "indisponível: a API responde sem cache").build();
    }
}
