package com.br.spbemestar.service.dashboard;

import com.br.spbemestar.dto.DashboardResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ActivityMetrics {

    private final MeterRegistry meters;

    public DashboardResponse.Activity read() {
        long requests = 0;
        long clientErrors = 0;
        long serverErrors = 0;
        double totalMs = 0;
        for (Timer timer : meters.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (uri != null && uri.startsWith("/actuator")) {
                continue;
            }
            requests += timer.count();
            totalMs += timer.totalTime(TimeUnit.MILLISECONDS);
            String outcome = timer.getId().getTag("outcome");
            if ("CLIENT_ERROR".equals(outcome)) {
                clientErrors += timer.count();
            } else if ("SERVER_ERROR".equals(outcome)) {
                serverErrors += timer.count();
            }
        }
        Map<String, Long> precheck = new TreeMap<>();
        meters.find("spbemestar.reports.precheck").counters()
                .forEach(c -> precheck.merge(c.getId().getTag("outcome"), (long) c.count(), Long::sum));
        return new DashboardResponse.Activity(requests, clientErrors, serverErrors,
                requests == 0 ? null : Math.round(totalMs / requests * 10) / 10.0,
                (long) meters.find("spbemestar.auth.logins").tag("result", "failure").counters().stream()
                        .mapToDouble(Counter::count).sum(),
                (long) meters.find("spbemestar.ratelimit.rejected").counters().stream().mapToDouble(Counter::count).sum(),
                precheck);
    }
}
