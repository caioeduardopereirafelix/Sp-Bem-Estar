package com.br.spbemestar.controller;

import com.br.spbemestar.dto.RefreshTokenRequest;
import com.br.spbemestar.dto.ReporterTokenResponse;
import com.br.spbemestar.service.ReporterTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Identidade anônima do cidadão: sem nome, e-mail ou senha, só um par de tokens emitido pela API. */
@RestController
@RequestMapping("/api/v1/reporters")
@RequiredArgsConstructor
public class ReporterController {

    private final ReporterTokenService reporterTokens;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReporterTokenResponse register() {
        return reporterTokens.register();
    }

    @PostMapping("/refresh")
    public ReporterTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return reporterTokens.refresh(request.refreshToken());
    }
}
