package com.br.spbemestar.service;

import com.br.spbemestar.dto.DashboardResponse;
import com.br.spbemestar.observability.RecentErrors;
import com.br.spbemestar.service.cache.JsonCache;
import com.br.spbemestar.service.dashboard.ActivityMetrics;
import com.br.spbemestar.service.dashboard.ProductMetrics;
import com.br.spbemestar.service.dashboard.SystemHealthProbe;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    static final Duration CACHE_TTL = Duration.ofSeconds(15);

    private final ProductMetrics product;
    private final SystemHealthProbe health;
    private final ActivityMetrics activity;
    private final RecentErrors recentErrors;
    private final JsonCache cache;
    private final Clock clock;

    public DashboardResponse dashboard() {
        return cache.get("ceo-dashboard", "geral", CACHE_TTL, DashboardResponse.class, this::build);
    }

    private DashboardResponse build() {
        List<DashboardResponse.RecentError> errors = recentErrors.latest(20).stream()
                .map(e -> new DashboardResponse.RecentError(e.at(), e.method(), e.path(), e.error(), e.message(),
                        e.traceId()))
                .toList();
        return new DashboardResponse(Instant.now(clock), product.read(), health.read(), activity.read(), errors);
    }
}
