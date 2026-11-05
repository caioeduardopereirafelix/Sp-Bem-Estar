package com.br.spbemestar.service;

import com.br.spbemestar.config.TraceContext;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final TraceContext traceContext;

    public void append(Long aggregateId, String type, Map<String, ?> payload, Instant now) {
        outbox.save(OutboxEvent.of(aggregateId, type, json.writeValueAsString(payload), now,
                traceContext.currentTraceParent()));
    }
}
