package com.br.spbemestar.dto;

import com.br.spbemestar.model.ModerationDecision;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * @param blockReporter além de rejeitar, bloqueia quem enviou o relato
 */
public record ModerationRequest(@NotNull ModerationDecision decision, boolean blockReporter) {

    @JsonIgnore
    @AssertTrue(message = "o autor só pode ser bloqueado ao rejeitar o relato")
    public boolean isBlockOnlyWhenRejecting() {
        return !blockReporter || decision == ModerationDecision.REJECTED;
    }
}
