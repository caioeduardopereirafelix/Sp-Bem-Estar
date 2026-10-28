package com.br.spbemestar.dto;

import java.time.Instant;

/**
 * @param expiresAt         até quando o código enviado vale
 * @param resendAfterSeconds quanto esperar para pedir outro
 */
public record LoginCodeSentResponse(Instant expiresAt, long resendAfterSeconds) {
}
