package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class ReportAlreadyModeratedException extends ApiException {

    public ReportAlreadyModeratedException(Long reportId, String decision) {
        super(HttpStatus.CONFLICT, "Relato já moderado",
                "O relato " + reportId + " já foi moderado (" + decision + ")");
    }
}
