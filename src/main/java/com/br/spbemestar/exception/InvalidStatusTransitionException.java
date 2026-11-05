package com.br.spbemestar.exception;

import com.br.spbemestar.model.IssueStatus;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException(IssueStatus from, IssueStatus to) {
        super(HttpStatus.CONFLICT, "Transição de status inválida",
                "Transição de status inválida: " + from + " -> " + to);
    }
}
