package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "Não encontrado", resource + " não encontrado: " + id);
    }
}
