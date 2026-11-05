package com.br.spbemestar.exception;

import org.springframework.http.HttpStatus;

public class UnknownCategoryException extends ApiException {

    public UnknownCategoryException(String code) {
        super(HttpStatus.BAD_REQUEST, "Categoria inválida", "Categoria desconhecida ou inativa: " + code);
    }
}
