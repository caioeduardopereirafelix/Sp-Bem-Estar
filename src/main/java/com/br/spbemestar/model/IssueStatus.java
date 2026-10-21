package com.br.spbemestar.model;

import java.util.EnumSet;
import java.util.Set;

public enum IssueStatus {

    OPEN,
    CONFIRMED,
    IN_PROGRESS,
    RESOLVED,
    /** Todos os relatos foram rejeitados pela moderação; só a moderação leva a este status. */
    DISCARDED;

    public Set<IssueStatus> allowedTransitions() {
        return switch (this) {
            case OPEN -> EnumSet.of(CONFIRMED, IN_PROGRESS, RESOLVED);
            case CONFIRMED -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(RESOLVED);
            case RESOLVED, DISCARDED -> EnumSet.noneOf(IssueStatus.class);
        };
    }

    public boolean canTransitionTo(IssueStatus target) {
        return allowedTransitions().contains(target);
    }
}
