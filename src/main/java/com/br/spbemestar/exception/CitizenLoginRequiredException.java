package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

public class CitizenLoginRequiredException extends ApiException {

    public CitizenLoginRequiredException() {
        super(HttpStatus.FORBIDDEN, "Entre para relatar", "Entre com o seu e-mail para enviar relatos.");
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("loginRequired", true);
    }
}
