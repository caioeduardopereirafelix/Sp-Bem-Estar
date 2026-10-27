package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.DashboardResponse;
import com.br.spbemestar.service.DashboardService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * O painel do CEO: números do produto, saúde do sistema e erros recentes. Só a conta do CEO abre; a equipe da
 * prefeitura, administrador incluído, recebe 403.
 */
@RestController
@RequiredArgsConstructor
public class CeoDashboardController {

    private final DashboardService dashboard;

    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @GetMapping("/api/v1/ceo/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboard.dashboard());
    }

    /** O endereço curto da página: {@code /ceo} leva a {@code /ceo.html}. */
    @GetMapping("/ceo")
    public ResponseEntity<Void> page() {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/ceo.html")).build();
    }
}
