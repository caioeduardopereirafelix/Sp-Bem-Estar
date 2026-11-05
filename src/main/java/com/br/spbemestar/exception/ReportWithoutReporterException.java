package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class ReportWithoutReporterException extends ApiException {

    public ReportWithoutReporterException(Long reportId) {
        super(HttpStatus.CONFLICT, "Relato sem autor", "O relato " + reportId + " foi enviado antes da identificação anônima e não tem autor para bloquear");
    }
}
