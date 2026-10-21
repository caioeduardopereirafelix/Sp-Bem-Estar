package com.br.spbemestar.exception;

public class ReportWithoutReporterException extends RuntimeException {

    public ReportWithoutReporterException(Long reportId) {
        super("O relato " + reportId + " foi enviado antes da identificação anônima e não tem autor para bloquear");
    }
}
