package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidPhotoException extends ApiException {

    public InvalidPhotoException(String message) {
        super(HttpStatus.BAD_REQUEST, "Foto inválida", message);
    }

    public InvalidPhotoException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "Foto inválida", message, cause);
    }
}
