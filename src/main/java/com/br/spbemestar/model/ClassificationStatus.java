package com.br.spbemestar.model;

import java.util.EnumSet;
import java.util.Set;

public enum ClassificationStatus {

    PENDING,
    AGREED,
    MISMATCH,
    LOW_CONFIDENCE;

    /** O modelo discordou da categoria ou não teve confiança: um operador precisa olhar. */
    public static final Set<ClassificationStatus> NEEDS_REVIEW = EnumSet.of(MISMATCH, LOW_CONFIDENCE);
}
