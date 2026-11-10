package com.br.spbemestar.service.cache;

import com.br.spbemestar.redis.RedisAvailability;
import com.br.spbemestar.util.Crypto;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
public class JsonCache {

    static final String PREFIX = "spbemestar:cache:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final MeterRegistry meters;
    private final RedisAvailability availability;
    private final boolean enabled;

    public JsonCache(StringRedisTemplate redis, JsonMapper json, MeterRegistry meters, RedisAvailability availability,
                     @Value("${app.cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.json = json;
        this.meters = meters;
        this.availability = availability;
        this.enabled = enabled;
    }

    public <T> T get(String cache, String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        return get(cache, key, ttl, json.constructType(type), loader);
    }

    public <T> T get(String cache, String key, Duration ttl, JavaType type, Supplier<T> loader) {
        if (!usable()) {
            count(cache, "bypass");
            return loader.get();
        }
        String redisKey = key(cache, key);
        String cached;
        try {
            cached = redis.opsForValue().get(redisKey);
        } catch (RuntimeException e) {
            failed(cache, e);
            return loader.get();
        }
        if (cached != null) {
            count(cache, "hit");
            return json.readValue(cached, type);
        }
        count(cache, "miss");
        T value = loader.get();
        try {
            redis.opsForValue().set(redisKey, json.writeValueAsString(value), ttl);
        } catch (RuntimeException e) {
            failed(cache, e);
        }
        return value;
    }

    public void evict(String cache) {
        if (!usable()) {
            return;
        }
        try {
            List<String> keys = new ArrayList<>();
            try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + cache + ":*")
                    .count(500).build())) {
                cursor.forEachRemaining(keys::add);
            }
            if (!keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (RuntimeException e) {
            failed(cache, e);
        }
    }

    public void evictAfterCommit(String cache) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(cache);
                }
            });
        } else {
            evict(cache);
        }
    }

    public boolean ping() {
        try {
            redis.execute(connection -> connection.ping(), true);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean usable() {
        return enabled && availability.usable();
    }

    private void failed(String cache, RuntimeException e) {
        count(cache, "error");
        availability.failed("Cache", e);
    }

    private void count(String cache, String result) {
        meters.counter("spbemestar.cache", "cache", cache, "result", result).increment();
    }

    static String key(String cache, String key) {
        String raw = key == null ? "" : key;
        if (raw.length() <= 120) {
            return PREFIX + cache + ":" + raw;
        }
        return PREFIX + cache + ":" + Crypto.sha256Hex(raw);
    }
}
