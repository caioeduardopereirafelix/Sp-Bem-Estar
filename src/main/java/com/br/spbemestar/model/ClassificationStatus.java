package com.br.spbemestar.model;

public enum ClassificationStatus {

    /** Aguardando o classificador. */
    PENDING,
    /** O modelo concordou com a categoria informada pelo cidadão. */
    AGREED,
    /** O modelo previu outra categoria: vai para moderação. */
    MISMATCH,
    /** Confiança abaixo do limite: vai para moderação. */
    LOW_CONFIDENCE
}
