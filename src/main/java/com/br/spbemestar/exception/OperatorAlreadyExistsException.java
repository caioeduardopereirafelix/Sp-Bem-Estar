package com.br.spbemestar.exception;

public class OperatorAlreadyExistsException extends RuntimeException {

    public OperatorAlreadyExistsException(String email) {
        super("Já existe um usuário com o e-mail " + email + ".");
    }
}
