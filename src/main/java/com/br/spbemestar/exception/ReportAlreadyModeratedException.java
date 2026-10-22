package com.br.spbemestar.exception;

public class ReportAlreadyModeratedException extends RuntimeException {

    public ReportAlreadyModeratedException(Long reportId, String decision) {
        super("O relato " + reportId + " já foi moderado (" + decision + ")");
    }
}
