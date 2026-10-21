package com.br.spbemestar.dto;

import java.time.Instant;

/**
 * Par de tokens do cidadão. O {@code accessToken} vai em {@code Authorization: Bearer} ao enviar um
 * relato; o {@code refreshToken} só serve para pedir um par novo e é trocado a cada uso.
 */
public record ReporterTokenResponse(String accessToken, String tokenType, Instant expiresAt,
                                    String refreshToken, Instant refreshTokenExpiresAt) {
}
