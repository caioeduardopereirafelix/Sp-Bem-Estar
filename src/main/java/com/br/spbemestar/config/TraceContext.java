package com.br.spbemestar.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class TraceContext {

    public static final String TRACE_PARENT = "traceparent";

    private final Tracer tracer;
    private final Propagator propagator;

    public TraceContext(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    public String currentTraceId() {
        Span span = tracer.currentSpan();
        return span == null ? null : span.context().traceId();
    }

    public String currentTraceParent() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACE_PARENT);
    }

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
