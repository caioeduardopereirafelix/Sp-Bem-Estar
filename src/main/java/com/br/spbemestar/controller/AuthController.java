package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.ChangePasswordRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;

import com.br.spbemestar.dto.LoginRequest;
import com.br.spbemestar.dto.TokenResponse;
import com.br.spbemestar.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** Troca a própria senha; os outros tokens deixam de valer e a resposta traz um novo. */
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PutMapping("/password")
    public TokenResponse changePassword(@AuthenticationPrincipal Jwt operator,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(Long.valueOf(operator.getSubject()), request.currentPassword(),
                request.newPassword());
    }
}
