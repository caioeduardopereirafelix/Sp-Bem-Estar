package com.br.spbemestar.model;

import java.util.EnumSet;
import java.util.Set;

public enum IssueStatus {

    OPEN("Aberto"),
    CONFIRMED("Confirmado"),
    IN_PROGRESS("Em andamento"),
    RESOLVED("Resolvido"),
    DISCARDED("Descartado");

    private final String label;

    IssueStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Set<IssueStatus> active() {
        return EnumSet.of(OPEN, CONFIRMED, IN_PROGRESS);
    }

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
