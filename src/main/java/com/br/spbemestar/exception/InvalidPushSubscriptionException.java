package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class InvalidPushSubscriptionException extends ApiException {

    public InvalidPushSubscriptionException(String message) {
        super(HttpStatus.BAD_REQUEST, "Inscrição inválida", message);
    }
}
