package com.br.spbemestar.controller;

import com.br.spbemestar.config.OpenApiConfig;
import com.br.spbemestar.dto.MyReportItem;
import com.br.spbemestar.dto.PageResponse;
import com.br.spbemestar.dto.PushConfigResponse;
import com.br.spbemestar.dto.PushSubscriptionRequest;
import com.br.spbemestar.dto.RefreshTokenRequest;
import com.br.spbemestar.dto.ReporterTokenResponse;
import com.br.spbemestar.service.MyReportsService;
import com.br.spbemestar.service.ReporterTokenService;
import com.br.spbemestar.service.push.PushNotificationService;
import com.br.spbemestar.service.push.WebPushSender;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reporters")
@RequiredArgsConstructor
public class ReporterController {

    private final ReporterTokenService reporterTokens;
    private final MyReportsService myReports;
    private final PushNotificationService notifications;
    private final WebPushSender pushSender;

    @PostMapping("/refresh")
    public ReporterTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return reporterTokens.refresh(request.refreshToken());
    }

    @SecurityRequirement(name = OpenApiConfig.REPORTER)
    @GetMapping("/me/reports")
    public PageResponse<MyReportItem> myReports(@AuthenticationPrincipal Jwt reporter,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return myReports.list(UUID.fromString(reporter.getSubject()), page, size);
    }

    @GetMapping("/push/config")
    public PushConfigResponse pushConfig() {
        return new PushConfigResponse(pushSender.enabled(), pushSender.publicKey());
    }

    @SecurityRequirement(name = OpenApiConfig.REPORTER)
    @PutMapping("/me/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal Jwt reporter, @Valid @RequestBody PushSubscriptionRequest request) {
        notifications.subscribe(UUID.fromString(reporter.getSubject()), request);
    }

    @SecurityRequirement(name = OpenApiConfig.REPORTER)
    @DeleteMapping("/me/push-subscription")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal Jwt reporter, @Valid @RequestBody PushSubscriptionRequest request) {
        notifications.unsubscribe(UUID.fromString(reporter.getSubject()), request.endpoint());
    }
}
