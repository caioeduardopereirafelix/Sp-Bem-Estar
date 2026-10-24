package com.br.spbemestar.service;

import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.dto.TokenResponse;
import com.br.spbemestar.exception.InvalidCredentialsException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final MeterRegistry meters;
    // Comparado quando o e-mail não existe, para a resposta levar o mesmo tempo e não revelar quem tem conta
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       SecurityProperties properties, Clock clock, MeterRegistry meters) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        this.meters = meters;
        this.dummyHash = passwordEncoder.encode("senha-que-nenhum-usuario-tem");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(String email, String password) {
        Optional<AppUser> user = users.findByEmail(AppUser.normalizeEmail(email));
        boolean matches = passwordEncoder.matches(password, user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches || !user.get().isActive()) {
            // sem o e-mail no log: ele é dado pessoal e, numa força bruta, a lista de tentativas vazaria
            log.warn("Login recusado");
            meters.counter("spbemestar.auth.logins", "result", "failure").increment();
            throw new InvalidCredentialsException();
        }
        log.info("Operador {} entrou", user.get().getId());
        meters.counter("spbemestar.auth.logins", "result", "success").increment();
        return issueToken(user.get());
    }

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
                .claim(SecurityConfig.ROLES_CLAIM, List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new TokenResponse(token, "Bearer", expiresAt, user.getName(), user.getRole().name());
    }

    @Transactional
    public AppUser createUser(String email, String name, String rawPassword, UserRole role) {
        if (rawPassword.length() < 12) {
            throw new IllegalArgumentException("A senha precisa ter ao menos 12 caracteres");
        }
        return users.save(AppUser.of(email, name, passwordEncoder.encode(rawPassword), role, Instant.now(clock)));
    }
}
