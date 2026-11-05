package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Renovação recusada",
                "Token de renovação inválido ou vencido. Peça uma identidade nova em POST /api/v1/reporters.");
    }
}
