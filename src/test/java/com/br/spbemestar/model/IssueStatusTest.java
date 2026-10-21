package com.br.spbemestar.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class IssueStatusTest {

    @ParameterizedTest
    @CsvSource({
            "OPEN, CONFIRMED, true",
            "OPEN, IN_PROGRESS, true",
            "OPEN, RESOLVED, true",
            "CONFIRMED, IN_PROGRESS, true",
            "CONFIRMED, RESOLVED, true",
            "IN_PROGRESS, RESOLVED, true",
            "CONFIRMED, OPEN, false",
            "IN_PROGRESS, OPEN, false",
            "IN_PROGRESS, CONFIRMED, false",
            "RESOLVED, OPEN, false",
            "RESOLVED, IN_PROGRESS, false",
            "OPEN, OPEN, false",
            // só a moderação descarta um chamado, e um chamado descartado não volta
            "OPEN, DISCARDED, false",
            "IN_PROGRESS, DISCARDED, false",
            "DISCARDED, OPEN, false",
            "DISCARDED, RESOLVED, false"
    })
    void validatesTransitions(IssueStatus from, IssueStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
