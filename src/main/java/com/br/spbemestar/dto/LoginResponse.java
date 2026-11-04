package com.br.spbemestar.dto;

import java.time.Instant;

public record LoginResponse(String role, String name, String email, String accessToken, String tokenType,
                            Instant expiresAt, String refreshToken, Instant refreshTokenExpiresAt) {

    public static final String CITIZEN = "CITIZEN";

    public static LoginResponse staff(TokenResponse token, String email) {
        return new LoginResponse(token.role(), token.name(), email, token.accessToken(), token.tokenType(),
                token.expiresAt(), null, null);
    }

    public static LoginResponse citizen(ReporterTokenResponse tokens, String email) {
        return new LoginResponse(CITIZEN, null, email, tokens.accessToken(), tokens.tokenType(), tokens.expiresAt(),
                tokens.refreshToken(), tokens.refreshTokenExpiresAt());
    }
}
