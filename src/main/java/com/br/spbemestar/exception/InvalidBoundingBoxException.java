package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidBoundingBoxException extends ApiException {

    public InvalidBoundingBoxException(String message) {
        super(HttpStatus.BAD_REQUEST, "Área inválida", message);
    }
}
