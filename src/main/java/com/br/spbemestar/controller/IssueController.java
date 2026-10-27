package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.ChangeStatusRequest;
import com.br.spbemestar.dto.GeoJsonFeatureCollection;
import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.dto.QueueItem;
import com.br.spbemestar.dto.StatusChangeResponse;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.service.IssueExportService;
import com.br.spbemestar.service.IssueQueryService;
import com.br.spbemestar.service.IssueStatusService;
import com.br.spbemestar.service.OperatorQueueService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueQueryService issueQueryService;
    private final IssueStatusService issueStatusService;
    private final OperatorQueueService operatorQueue;
    private final IssueExportService issueExport;

    /**
     * Sem {@code status}, devolve apenas chamados não resolvidos.
     */
    @GetMapping
    public GeoJsonFeatureCollection search(@RequestParam(required = false) String bbox,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) IssueStatus status,
                                           @RequestParam(defaultValue = "500") @Min(1) @Max(1000) int limit) {
        BoundingBox box = bbox == null ? null : BoundingBox.parse(bbox);
        return issueQueryService.search(box, category, status, limit);
    }

    /**
     * Fila de atendimento do operador: sem {@code status}, os chamados abertos, confirmados e em andamento,
     * do maior {@code priorityScore} para o menor.
     */
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @GetMapping("/queue")
    public PageResponse<QueueItem> queue(@RequestParam(required = false) IssueStatus status,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) Long neighborhoodId,
                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return operatorQueue.queue(status, category, neighborhoodId, page, size);
    }

    /**
     * Planilha dos chamados (CSV para o Excel em português), para a prefeitura levar ao sistema dela. Sem
     * {@code status}, todos menos os descartados; {@code from} e {@code to} filtram pela data do primeiro relato.
     */
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @GetMapping(value = "/export", produces = "text/csv")
    public void export(@RequestParam(required = false) IssueStatus status,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @AuthenticationPrincipal Jwt operator, HttpServletResponse response) throws IOException {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data inicial vem depois da final.");
        }
        String today = LocalDate.now(IssueExportService.CITY_ZONE).toString();
        response.setContentType("text/csv");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("chamados-" + today + ".csv").build().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        issueExport.writeCsv(response.getWriter(), status, category, from, to, baseUrl,
                Long.valueOf(operator.getSubject()));
    }

    /** O nome de quem mudou cada status só aparece para operadores; o mapa público vê o histórico sem nomes. */
    @GetMapping("/{id}")
    public IssueDetailResponse detail(@PathVariable Long id, @AuthenticationPrincipal Jwt viewer) {
        List<String> roles = viewer == null ? null : viewer.getClaimAsStringList(SecurityConfig.ROLES_CLAIM);
        return issueQueryService.detail(id, roles != null && roles.contains(UserRole.OPERATOR.name()));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PatchMapping("/{id}/status")
    public StatusChangeResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request,
                                             @AuthenticationPrincipal Jwt operator) {
        return issueStatusService.change(id, request.status(), Long.valueOf(operator.getSubject()));
    }

    /**
     * Resolve o chamado mostrando o que foi feito: a foto do conserto ({@code photo}, JPEG ou PNG) e uma nota curta
     * ({@code note}, até 500 caracteres), as duas opcionais e públicas no mapa.
     */
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PostMapping(value = "/{id}/resolution", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public StatusChangeResponse resolve(@PathVariable Long id,
                                        @RequestPart(value = "photo", required = false) MultipartFile photo,
                                        @RequestParam(required = false) String note,
                                        @AuthenticationPrincipal Jwt operator) throws IOException {
        byte[] content = photo == null || photo.isEmpty() ? null : photo.getBytes();
        return issueStatusService.resolve(id, Long.valueOf(operator.getSubject()), content, note);
    }
}
