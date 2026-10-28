package com.br.spbemestar.dto;

import java.time.Instant;

/**
 * O resultado do login por código. O {@code role} diz quem entrou: {@code CITIZEN} recebe o par de tokens do
 * cidadão (com {@code refreshToken}); {@code OPERATOR}, {@code ADMIN} e {@code CEO} recebem o token da equipe,
 * que não se renova e vence em {@code expiresAt}.
 */
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
