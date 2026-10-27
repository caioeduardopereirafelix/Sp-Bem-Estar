package com.br.spbemestar.exception;

public class CitizenLoginRequiredException extends RuntimeException {

    public CitizenLoginRequiredException() {
        super("Entre com o seu e-mail para enviar relatos.");
    }
}
