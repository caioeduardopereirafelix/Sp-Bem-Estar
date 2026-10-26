package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.CreateOperatorRequest;
import com.br.spbemestar.dto.OperatorItem;
import com.br.spbemestar.dto.UpdateOperatorRequest;
import com.br.spbemestar.service.OperatorService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Equipe do órgão público: só o administrador lista, cadastra, desativa e reativa operadores. */
@SecurityRequirement(name = OpenApiConfig.BEARER)
@RestController
@RequestMapping("/api/v1/operators")
@RequiredArgsConstructor
public class OperatorController {

    private final OperatorService operators;

    @GetMapping
    public List<OperatorItem> list() {
        return operators.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OperatorItem create(@AuthenticationPrincipal Jwt admin, @Valid @RequestBody CreateOperatorRequest request) {
        return operators.create(Long.valueOf(admin.getSubject()), request);
    }

    /** {@code active: false} corta o acesso na hora, mesmo com um token ainda dentro da validade. */
    @PatchMapping("/{id}")
    public OperatorItem update(@AuthenticationPrincipal Jwt admin, @PathVariable Long id,
                               @Valid @RequestBody UpdateOperatorRequest request) {
        return operators.setActive(Long.valueOf(admin.getSubject()), id, request.active());
    }
}
