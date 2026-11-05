package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class PushDisabledException extends ApiException {

    public PushDisabledException() {
        super(HttpStatus.NOT_FOUND, "Notificações desligadas",
                "As notificações push não estão configuradas neste servidor.");
    }
}
