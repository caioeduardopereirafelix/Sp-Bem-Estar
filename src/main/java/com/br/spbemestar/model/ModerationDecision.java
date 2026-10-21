package com.br.spbemestar.model;

public enum ModerationDecision {

    /** O relato é válido e continua contando no chamado. */
    APPROVED,
    /** O relato não vale (spam, foto que não mostra o problema): sai do chamado e a foto é apagada. */
    REJECTED
}
