package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class OperatorAlreadyExistsException extends ApiException {

    public OperatorAlreadyExistsException(String email) {
        super(HttpStatus.CONFLICT, "E-mail já cadastrado", "Já existe um usuário com o e-mail " + email + ".");
    }
}
