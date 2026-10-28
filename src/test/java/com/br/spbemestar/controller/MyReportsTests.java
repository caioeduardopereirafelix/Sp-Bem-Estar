package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ClassificationService;
import com.br.spbemestar.service.IssueStatusService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import com.br.spbemestar.service.push.TestVapidKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MyReportsTests {

    @Autowired MockMvcTester mvc;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired ReporterJwt reporterJwt;
    @Autowired ReportService reportService;
    @Autowired IssueStatusService statusService;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired ClassificationService classification;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listsOwnReportsNewestFirstWithIssueProgress() {
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        UUID me = subject(token);
        Long first = reportService.create(me, "POTHOLE", -25.10, -47.10, TestPhotos.jpeg()).issueId();
        Long second = reportService.create(me, "TRASH", -25.20, -47.20, TestPhotos.jpeg()).issueId();
        statusService.change(first, IssueStatus.IN_PROGRESS, operatorId());
        // de outro cidadão: não aparece
        reportService.create(subject(reporterTokens.issueFor(UUID.randomUUID()).accessToken()), "POTHOLE", -25.30, -47.30, TestPhotos.jpeg());

        MvcTestResult result = myReports("Bearer " + token);

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.total").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.items[*].issueId").asArray().containsExactly(
                second.intValue(), first.intValue());
        assertThat(result).bodyJson().extractingPath("$.items[0].issueStatus").isEqualTo("OPEN");
        assertThat(result).bodyJson().extractingPath("$.items[0].categoryName").isEqualTo("Descarte irregular de lixo ou entulho");
        assertThat(result).bodyJson().extractingPath("$.items[1].issueStatus").isEqualTo("IN_PROGRESS");
        assertThat(result).bodyJson().extractingPath("$.items[1].history[0].to").isEqualTo("IN_PROGRESS");
        // o cidadão vê o que mudou, não quem mudou
        assertThat(result).bodyJson().extractingPath("$.items[1].history[0].changedBy").isNull();
        assertThat(result).bodyJson().extractingPath("$.items[1].rejected").isEqualTo(false);
    }

    @Test
    void showsWhatTheClassifierSawInThePhotoUntilModerationDecides() {
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        Long reportId = reportService.create(subject(token), "POTHOLE", -25.40, -47.40, TestPhotos.jpeg()).reportId();

        // logo depois do envio: o classificador ainda não respondeu
        assertThat(myReports("Bearer " + token)).bodyJson().extractingPath("$.items[0].photoCheck.status")
                .isEqualTo("PENDING");

        // o modelo não viu um buraco na foto, e sim nenhum problema
        classification.apply(reportId, "OTHER", 0.93);
        MvcTestResult checked = myReports("Bearer " + token);
        assertThat(checked).bodyJson().extractingPath("$.items[0].photoCheck.status").isEqualTo("MISMATCH");
        assertThat(checked).bodyJson().extractingPath("$.items[0].photoCheck.predictedCategory").isEqualTo("OTHER");

        // a moderação aprovou: o palpite do modelo deixa de valer para o cidadão
        jdbc.update("UPDATE report SET moderation_status = 'APPROVED', moderated_at = now(), moderated_by = ? WHERE id = ?",
                operatorId(), reportId);
        assertThat(myReports("Bearer " + token)).bodyJson().extractingPath("$.items[0].photoCheck").isNull();
    }

    @Test
    void newIdentityStartsWithoutReports() {
        assertThat(myReports("Bearer " + reporterTokens.issueFor(UUID.randomUUID()).accessToken())).hasStatusOk()
                .bodyJson().extractingPath("$.total").isEqualTo(0);
    }

    @Test
    void requiresTheCitizenToken() {
        assertThat(myReports(null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(myReports(TestOperators.bearer(auth, users))).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pushIsOffWithoutVapidKeys() {
        assertThat(mvc.get().uri("/api/v1/reporters/push/config").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.enabled").isEqualTo(false);

        String[] keys = TestVapidKeys.browserKeys();
        MvcTestResult subscribe = mvc.put().uri("/api/v1/reporters/me/push-subscription")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + reporterTokens.issueFor(UUID.randomUUID()).accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"endpoint": "https://fcm.googleapis.com/fcm/send/abc", "keys": {"p256dh": "%s", "auth": "%s"}}
                        """.formatted(keys[0], keys[1]))
                .exchange();
        assertThat(subscribe).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Notificações desligadas");
    }

    private MvcTestResult myReports(String authorization) {
        var request = mvc.get().uri("/api/v1/reporters/me/reports");
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private UUID subject(String token) {
        return UUID.fromString(reporterJwt.decoder().decode(token).getSubject());
    }

    private Long operatorId() {
        TestOperators.bearer(auth, users);
        return users.findByEmail(TestOperators.EMAIL).orElseThrow().getId();
    }
}
