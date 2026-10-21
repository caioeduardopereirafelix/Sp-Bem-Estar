package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.ModerationItem;
import com.br.spbemestar.dto.ModerationRequest;
import com.br.spbemestar.dto.ModerationResponse;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.service.ModerationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = OpenApiConfig.BEARER)
@RestController
@RequestMapping("/api/v1/moderation/reports")
@RequiredArgsConstructor
public class ModerationController {

    private final ModerationService moderation;

    /** Relatos que o classificador marcou e ainda esperam decisão, do mais antigo para o mais novo. */
    @GetMapping
    public PageResponse<ModerationItem> pending(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return moderation.pending(page, size);
    }

    @PostMapping("/{id}")
    public ModerationResponse decide(@PathVariable Long id, @Valid @RequestBody ModerationRequest request,
                                     @AuthenticationPrincipal Jwt operator) {
        return moderation.decide(id, request, Long.valueOf(operator.getSubject()));
    }
}
