package com.br.spbemestar.dto;

import java.time.Instant;

public record ReporterTokenResponse(String accessToken, String tokenType, Instant expiresAt,
                                    String refreshToken, Instant refreshTokenExpiresAt) {
}
