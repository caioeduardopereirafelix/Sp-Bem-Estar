package com.br.spbemestar.config;

import java.time.Duration;

public interface RateLimitStore {

    Probe consume(String key, int capacity, Duration period);

    record Probe(boolean allowed, long remaining, long nanosToWait) {
    }
}
