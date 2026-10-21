package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.CreateReportRequest;
import com.br.spbemestar.dto.ReportResponse;
import com.br.spbemestar.exception.InvalidPhotoException;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReporterTokenService reporterTokens;

    @SecurityRequirement(name = OpenApiConfig.REPORTER)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(@RequestPart("photo") MultipartFile photo,
                                 @Valid @ModelAttribute CreateReportRequest request,
                                 @AuthenticationPrincipal Jwt reporter) throws IOException {
        if (photo.isEmpty()) {
            throw new InvalidPhotoException("A foto é obrigatória");
        }
        return ReportResponse.from(reportService.create(UUID.fromString(reporter.getSubject()),
                request.category(), request.latitude(), request.longitude(), photo.getBytes()));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @PostMapping("/{id}/block-reporter")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockReporter(@PathVariable Long id, @AuthenticationPrincipal Jwt operator) {
        reporterTokens.blockAuthorOf(id, Long.valueOf(operator.getSubject()));
    }
}
