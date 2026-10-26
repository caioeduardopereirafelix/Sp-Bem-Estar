package com.br.spbemestar.exception;

/** Senha nova fora das regras ou senha atual errada na troca. */
public class InvalidPasswordException extends IllegalArgumentException {

    public InvalidPasswordException(String message) {
        super(message);
    }
}
