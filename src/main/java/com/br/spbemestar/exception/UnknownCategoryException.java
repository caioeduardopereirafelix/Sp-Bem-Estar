package com.br.spbemestar.exception;

public class UnknownCategoryException extends RuntimeException {

    public UnknownCategoryException(String code) {
        super("Categoria desconhecida ou inativa: " + code);
    }
}
