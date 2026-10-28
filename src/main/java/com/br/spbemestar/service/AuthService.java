package com.br.spbemestar.service;

import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.dto.TokenResponse;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Usuários da equipe (e o CEO) e o token deles. Ninguém tem senha: a entrada é pelo código enviado por e-mail. */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository users;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public TokenResponse issueToken(AppUser user) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .claim(SecurityConfig.ROLES_CLAIM, user.getRole().grantedRoles())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt, user.getName(), user.getRole().name());
    }

    @Transactional
    public AppUser createUser(String email, String name, UserRole role) {
        return users.save(AppUser.of(email, name, role, Instant.now(clock)));
    }
}
