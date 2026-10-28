package com.br.spbemestar.exception;

import lombok.Getter;

@Getter
public class LoginCodeThrottledException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginCodeThrottledException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
