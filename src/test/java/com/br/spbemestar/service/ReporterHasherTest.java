package com.br.spbemestar.service;

import com.br.spbemestar.config.ReporterProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReporterHasherTest {

    private static final UUID REPORTER = UUID.fromString("7f2c1a9e-3b4d-4e5f-8a6b-0c1d2e3f4a5b");

    @Test
    void sameReporterAndKeyGiveTheSameHash() {
        ReporterHasher hasher = hasher("chave-de-teste-com-pelo-menos-32-bytes");

        assertThat(hasher.reporterHash(REPORTER)).isEqualTo(hasher.reporterHash(REPORTER)).hasSize(64)
                .isNotEqualTo(hasher.reporterHash(UUID.randomUUID()));
    }

    @Test
    void withoutTheKeyTheHashCannotBeRecomputed() {
        assertThat(hasher("chave-de-teste-com-pelo-menos-32-bytes").reporterHash(REPORTER))
                .isNotEqualTo(hasher("outra-chave-tambem-com-32-bytes-ou-mais").reporterHash(REPORTER));
    }

    @Test
    void refusesShortOrMissingKeys() {
        assertThatThrownBy(() -> hasher("curta")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPORTER_HASH");
        assertThatThrownBy(() -> hasher(null)).isInstanceOf(IllegalStateException.class);
    }

    private static ReporterHasher hasher(String secret) {
        return new ReporterHasher(new ReporterProperties(secret, Duration.ofMinutes(15), Duration.ofDays(90),
                "spbemestar-reporter"));
    }
}
