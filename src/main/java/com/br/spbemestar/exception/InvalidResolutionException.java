package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidResolutionException extends ApiException {

    public InvalidResolutionException(String message) {
        super(HttpStatus.BAD_REQUEST, "Resolução inválida", message);
    }
}
