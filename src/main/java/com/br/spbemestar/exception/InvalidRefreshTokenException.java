package com.br.spbemestar.exception;

public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Token de renovação inválido ou vencido. Peça uma identidade nova em POST /api/v1/reporters.");
    }
}
