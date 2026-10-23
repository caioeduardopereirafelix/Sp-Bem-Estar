package com.br.spbemestar.classifier.exception;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Falha definitiva: a mensagem vai para a DLQ. O motivo vira o rótulo {@code outcome} da métrica. */
public class RejectedReportException extends AmqpRejectAndDontRequeueException {

    private final String reason;

    public RejectedReportException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
