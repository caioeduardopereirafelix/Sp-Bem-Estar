package com.br.spbemestar.exception;

public class StaffAccessDisabledException extends RuntimeException {

    public StaffAccessDisabledException() {
        super("Este acesso da equipe foi desativado. Fale com o administrador da prefeitura.");
    }
}
