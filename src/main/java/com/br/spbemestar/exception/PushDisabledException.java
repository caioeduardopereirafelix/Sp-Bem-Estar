package com.br.spbemestar.exception;

public class PushDisabledException extends RuntimeException {

    public PushDisabledException() {
        super("As notificações push não estão configuradas neste servidor.");
    }
}
