package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class StaffAccessDisabledException extends ApiException {

    public StaffAccessDisabledException() {
        super(HttpStatus.FORBIDDEN, "Acesso desativado", "Este acesso da equipe foi desativado. Fale com o administrador da prefeitura.");
    }
}
