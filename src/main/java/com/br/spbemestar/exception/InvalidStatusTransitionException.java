package com.br.spbemestar.exception;

import com.br.spbemestar.model.IssueStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(IssueStatus from, IssueStatus to) {
        super("Transição de status inválida: " + from + " -> " + to);
    }
}
