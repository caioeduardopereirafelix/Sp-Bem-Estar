package com.br.spbemestar.config;

import com.br.spbemestar.redis.RedisAvailability;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

public class RedisRateLimitStore implements RateLimitStore {

    static final String PREFIX = "spbemestar:ratelimit:";

    private static final RedisScript<List> TOKEN_BUCKET = RedisScript.of("""
            local capacity = tonumber(ARGV[1])
            local period = tonumber(ARGV[2])
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000000 + tonumber(time[2])
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(state[1]) or capacity
            local at = tonumber(state[2]) or now
            tokens = math.min(capacity, tokens + math.max(0, now - at) * capacity / period)
            local allowed = 0
            local wait = 0
            if tokens >= 1 then
                tokens = tokens - 1
                allowed = 1
            else
                wait = math.ceil((1 - tokens) * period / capacity)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'at', string.format('%d', now))
            redis.call('PEXPIRE', KEYS[1], math.ceil(period / 1000))
            return {allowed, math.floor(tokens), wait}
            """, List.class);

    private final StringRedisTemplate redis;
    private final RedisAvailability availability;
    private final RateLimitStore fallback;

    public RedisRateLimitStore(StringRedisTemplate redis, RedisAvailability availability, RateLimitStore fallback) {
        this.redis = redis;
        this.availability = availability;
        this.fallback = fallback;
    }

    @Override
    public Probe consume(String key, int capacity, Duration period) {
        if (!availability.usable()) {
            return fallback.consume(key, capacity, period);
        }
        try {
            List<?> result = redis.execute(TOKEN_BUCKET, List.of(PREFIX + key), String.valueOf(capacity),
                    String.valueOf(period.toNanos() / 1000));
            return new Probe(number(result, 0) == 1, number(result, 1), number(result, 2) * 1000);
        } catch (RuntimeException e) {
            availability.failed("Rate limit compartilhado", e);
            return fallback.consume(key, capacity, period);
        }
    }

    private static long number(List<?> result, int index) {
        return ((Number) result.get(index)).longValue();
    }
}
