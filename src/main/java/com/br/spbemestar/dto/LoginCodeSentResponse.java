package com.br.spbemestar.dto;

import java.time.Instant;

public record LoginCodeSentResponse(Instant expiresAt, long resendAfterSeconds) {
}
