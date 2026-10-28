package com.br.spbemestar.observability;

import com.br.spbemestar.model.Category;
import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.ModerationDecision;
import com.br.spbemestar.repository.CategoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Registra com zero os contadores de negócio na subida. Um contador do Micrometer só aparece no
 * primeiro incremento, e o Prometheus não enxerga esse primeiro aumento: a série já nasce valendo 1,
 * então {@code increase()} dá 0 e um alerta como o de refresh reutilizado nunca dispararia na
 * primeira vez depois de um deploy.
 */
@Component
@RequiredArgsConstructor
public class CounterWarmup {

    private static final List<String> REPORTER_EVENTS =
            List.of("registered", "refreshed", "refresh_reused", "refresh_blocked", "blocked");

    private final MeterRegistry meters;
    private final CategoryRepository categories;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        categories.findAll().stream().map(Category::getCode)
                .forEach(code -> meters.counter("spbemestar.reports.received", "category", code));
        for (ClassificationStatus status : ClassificationStatus.values()) {
            if (status != ClassificationStatus.PENDING) {
                meters.counter("spbemestar.reports.classified", "status", status.name());
            }
        }
        for (ModerationDecision decision : ModerationDecision.values()) {
            meters.counter("spbemestar.moderation.decisions", "decision", decision.name(), "blockReporter", "false");
        }
        meters.counter("spbemestar.moderation.decisions", "decision", ModerationDecision.REJECTED.name(),
                "blockReporter", "true");
        for (IssueStatus status : IssueStatus.values()) {
            if (status != IssueStatus.OPEN) {
                meters.counter("spbemestar.issues.status.changes", "to", status.name());
            }
        }
        REPORTER_EVENTS.forEach(event -> meters.counter("spbemestar.reporters.events", "event", event));
        List.of("success", "failure").forEach(result -> meters.counter("spbemestar.auth.logins", "result", result));
        List.of("sent", "throttled").forEach(result -> meters.counter("spbemestar.auth.codes", "result", result));
        List.of("delivered", "expired", "failed").forEach(result -> meters.counter("spbemestar.push.sent", "result", result));
    }
}
