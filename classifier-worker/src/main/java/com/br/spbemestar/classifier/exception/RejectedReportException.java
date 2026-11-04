package com.br.spbemestar.classifier.exception;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

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
