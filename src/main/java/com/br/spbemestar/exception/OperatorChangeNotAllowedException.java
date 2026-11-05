package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class OperatorChangeNotAllowedException extends ApiException {

    public OperatorChangeNotAllowedException(String message) {
        super(HttpStatus.CONFLICT, "Alteração não permitida", message);
    }
}
