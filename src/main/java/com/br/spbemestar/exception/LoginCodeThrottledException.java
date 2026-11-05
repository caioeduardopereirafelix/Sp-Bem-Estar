package com.br.spbemestar.exception;

import lombok.Getter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@Getter
public class LoginCodeThrottledException extends ApiException {

    private final long retryAfterSeconds;

    public LoginCodeThrottledException(String message, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Espere para pedir outro código", message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        return headers;
    }
}
