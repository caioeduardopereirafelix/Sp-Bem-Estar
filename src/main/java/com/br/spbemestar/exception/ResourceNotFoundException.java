package com.br.spbemestar.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " não encontrado: " + id);
    }
}
