package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class ReporterBlockedException extends ApiException {

    public ReporterBlockedException() {
        super(HttpStatus.FORBIDDEN, "Autor bloqueado", "Este autor foi bloqueado pela moderação e não pode enviar relatos.");
    }
}
