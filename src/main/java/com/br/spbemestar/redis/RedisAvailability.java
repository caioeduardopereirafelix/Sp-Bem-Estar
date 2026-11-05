package com.br.spbemestar.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class RedisAvailability {

    public static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(30);

    private final Clock clock;
    private volatile Instant pausedUntil = Instant.EPOCH;

    public RedisAvailability(Clock clock) {
        this.clock = clock;
    }

    public boolean usable() {
        return !Instant.now(clock).isBefore(pausedUntil);
    }

    public void failed(String feature, RuntimeException e) {
        pausedUntil = Instant.now(clock).plus(PAUSE_AFTER_FAILURE);
        log.warn("{} indisponível; seguindo sem o Redis por {} s: {}", feature, PAUSE_AFTER_FAILURE.toSeconds(),
                e.getMessage());
    }
}
