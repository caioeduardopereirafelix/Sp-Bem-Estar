package com.br.spbemestar.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, String name, String role) {
}
