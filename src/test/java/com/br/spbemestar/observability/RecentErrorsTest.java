package com.br.spbemestar.observability;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RecentErrorsTest {

    final RecentErrors errors = new RecentErrors(Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void keepsTheNewestFirstAndForgetsTheOldestWhenFull() {
        for (int i = 0; i < RecentErrors.CAPACITY + 5; i++) {
            errors.record("GET", "/api/v1/erro/" + i, new IllegalStateException("falha " + i), "trace-" + i);
        }

        assertThat(errors.latest(100)).hasSize(RecentErrors.CAPACITY);
        assertThat(errors.latest(2)).extracting(RecentErrors.Entry::path)
                .containsExactly("/api/v1/erro/54", "/api/v1/erro/53");
        assertThat(errors.latest(100).getLast().path()).isEqualTo("/api/v1/erro/5");
    }

    @Test
    void shortensLongMessagesAndToleratesNone() {
        errors.record("POST", "/api/v1/reports", new RuntimeException("x".repeat(500)), null);
        errors.record("GET", "/api/v1/issues", new NullPointerException(), "abc");

        RecentErrors.Entry withoutMessage = errors.latest(2).get(0);
        assertThat(withoutMessage.error()).isEqualTo("NullPointerException");
        assertThat(withoutMessage.message()).isEmpty();
        assertThat(withoutMessage.at()).isEqualTo(Instant.parse("2026-10-27T12:00:00Z"));
        assertThat(errors.latest(2).get(1).message()).hasSize(RecentErrors.MAX_MESSAGE + 1).endsWith("…");
    }
}
