package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidLoginCodeException extends ApiException {

    public InvalidLoginCodeException() {
        super(HttpStatus.UNAUTHORIZED, "Código inválido", "Código errado ou vencido. Confira o último e-mail ou peça um código novo.");
    }
}
