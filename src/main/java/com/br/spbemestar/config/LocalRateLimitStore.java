package com.br.spbemestar.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import java.time.Clock;
import java.time.Duration;

public class LocalRateLimitStore implements RateLimitStore {

    private final Cache<String, Bucket> buckets;
    private final TimeMeter time;

    public LocalRateLimitStore(int maxTrackedClients, Duration longestPeriod, Clock clock) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maxTrackedClients)
                .expireAfterAccess(longestPeriod)
                .build();
        this.time = new ClockTimeMeter(clock);
    }

    @Override
    public Probe consume(String key, int capacity, Duration period) {
        Bucket bucket = buckets.get(key, k -> Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, period))
                .withCustomTimePrecision(time)
                .build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        return new Probe(probe.isConsumed(), probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
    }

    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            var now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000 + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
