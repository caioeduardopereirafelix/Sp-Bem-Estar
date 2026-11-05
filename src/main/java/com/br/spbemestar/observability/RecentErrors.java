package com.br.spbemestar.observability;

import com.br.spbemestar.redis.RedisAvailability;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Component
public class RecentErrors {

    static final int CAPACITY = 50;
    static final int MAX_MESSAGE = 200;
    static final String KEY = "spbemestar:recent-errors";

    private final Deque<Entry> local = new ArrayDeque<>(CAPACITY);
    private final Clock clock;
    private final StringRedisTemplate redis;
    private final RedisAvailability availability;
    private final JsonMapper json;

    public RecentErrors(Clock clock) {
        this(clock, null, null, null);
    }

    @Autowired
    public RecentErrors(Clock clock, StringRedisTemplate redis, RedisAvailability availability, JsonMapper json) {
        this.clock = clock;
        this.redis = redis;
        this.availability = availability;
        this.json = json;
    }

    public void record(String method, String path, Throwable error, String traceId) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        Entry entry = new Entry(Instant.now(clock), method, path, error.getClass().getSimpleName(),
                message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) + "…" : message, traceId);
        synchronized (local) {
            if (local.size() == CAPACITY) {
                local.removeLast();
            }
            local.addFirst(entry);
        }
        if (shared()) {
            try {
                redis.opsForList().leftPush(KEY, json.writeValueAsString(entry));
                redis.opsForList().trim(KEY, 0, CAPACITY - 1);
            } catch (RuntimeException e) {
                availability.failed("Lista de erros compartilhada", e);
            }
        }
    }

    public List<Entry> latest(int limit) {
        if (shared()) {
            try {
                List<String> stored = redis.opsForList().range(KEY, 0, limit - 1);
                if (stored != null) {
                    return stored.stream().map(value -> json.readValue(value, Entry.class)).toList();
                }
            } catch (RuntimeException e) {
                availability.failed("Lista de erros compartilhada", e);
            }
        }
        synchronized (local) {
            return local.stream().limit(limit).toList();
        }
    }

    private boolean shared() {
        return redis != null && availability.usable();
    }

    public record Entry(Instant at, String method, String path, String error, String message, String traceId) {
    }
}
