package com.br.spbemestar.exception;

public class InvalidLoginCodeException extends RuntimeException {

    public InvalidLoginCodeException() {
        super("Código errado ou vencido. Confira o último e-mail ou peça um código novo.");
    }
}
