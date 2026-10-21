package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.ChangeStatusRequest;
import com.br.spbemestar.dto.GeoJsonFeatureCollection;
import com.br.spbemestar.dto.IssueDetailResponse;
import com.br.spbemestar.dto.StatusChangeResponse;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.service.IssueQueryService;
import com.br.spbemestar.service.IssueStatusService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueQueryService issueQueryService;
    private final IssueStatusService issueStatusService;

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

    @GetMapping("/{id}")
    public IssueDetailResponse detail(@PathVariable Long id) {
        return issueQueryService.detail(id);
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PatchMapping("/{id}/status")
    public StatusChangeResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
        return issueStatusService.change(id, request.status());
    }
}
