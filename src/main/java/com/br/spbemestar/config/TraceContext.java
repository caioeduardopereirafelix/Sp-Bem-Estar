package com.br.spbemestar.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ponte entre o trace atual e o que precisa atravessar um intervalo: a outbox grava o {@code traceparent}
 * de quem criou o evento, e o publicador continua aquele trace ao enviar a mensagem.
 */
@Component
public class TraceContext {

    public static final String TRACE_PARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContext(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    /** {@code traceId} atual, que vai no header {@code X-Trace-Id} e nas respostas de erro. */
    public String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    /** {@code traceparent} W3C do trace atual, ou {@code null} fora de um trace. */
    public String currentTraceParent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACE_PARENT);
    }

    /** Roda a ação num span filho do {@code traceparent} gravado; sem ele, no trace atual. */
    public <T> T continueTrace(String traceParent, String spanName, Supplier<T> action) {
        if (traceParent == null) {
            return action.get();
        }
        Span span = propagator.extract(traceParent, (carrier, key) -> TRACE_PARENT.equals(key) ? carrier : null)
                .name(spanName)
                .kind(Span.Kind.PRODUCER)
                .start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return action.get();
        } catch (RuntimeException e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
