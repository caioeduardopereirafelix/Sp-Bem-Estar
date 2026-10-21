package com.br.spbemestar.exception;

public class ReporterBlockedException extends RuntimeException {

    public ReporterBlockedException() {
        super("Este autor foi bloqueado pela moderação e não pode enviar relatos.");
    }
}
