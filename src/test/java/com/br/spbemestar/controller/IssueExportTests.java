package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.IssueStatusService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IssueExportTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;
    @Autowired IssueStatusService statusService;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void exportsASpreadsheetThatExcelOpensInPortuguese() {
        Long open = newIssue("POTHOLE", -27.00);
        Long resolved = newIssue("TRASH", -27.01);
        // uma nota maliciosa não pode virar fórmula na planilha, e o ";" não pode quebrar a coluna
        statusService.resolve(resolved, operatorId(), null, "=HYPERLINK(\"http://x\"); limpo");

        MvcTestResult result = export("");

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .startsWith("attachment").contains("chamados-").contains(".csv");
        assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        String body = body(result);
        assertThat(body).startsWith("﻿Chamado;Tipo;Situação;Distrito;Latitude;Longitude;");

        List<String> openRow = cells(line(body, open));
        assertThat(openRow.get(2)).isEqualTo("Aberto");
        assertThat(openRow.get(4)).isEqualTo("-27,000000"); // vírgula decimal: o Excel lê como número
        assertThat(openRow.getLast()).isEqualTo("http://localhost/?issue=" + open);

        String resolvedLine = line(body, resolved);
        assertThat(cells(resolvedLine).get(2)).isEqualTo("Resolvido");
        assertThat(resolvedLine).contains("\"'=HYPERLINK(\"\"http://x\"\"); limpo\"");
        // nada do cidadão vai para a planilha
        assertThat(body).doesNotContain(TestReporters.ID.toString());
    }

    @Test
    void filtersByStatusCategoryAndDate() {
        Long pothole = newIssue("POTHOLE", -27.02);
        Long trash = newIssue("TRASH", -27.03);
        jdbc.update("UPDATE issue SET first_reported_at = '2020-01-15T12:00:00Z' WHERE id = ?", trash);

        String byCategory = body(export("?category=POTHOLE&status=OPEN"));
        assertThat(byCategory).contains(lineStart(pothole)).doesNotContain(lineStart(trash));

        String byDate = body(export("?from=2020-01-15&to=2020-01-15"));
        assertThat(byDate).contains(lineStart(trash)).doesNotContain(lineStart(pothole));
    }

    @Test
    void leavesOutIssuesDiscardedByModeration() {
        Long discarded = newIssue("POTHOLE", -27.04);
        jdbc.update("UPDATE issue SET status = 'DISCARDED' WHERE id = ?", discarded);

        assertThat(body(export(""))).doesNotContain(lineStart(discarded));
    }

    @Test
    void rejectsAnInvertedDateRange() {
        assertThat(export("?from=2026-02-01&to=2026-01-01")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyOperatorsExport() {
        assertThat(mvc.get().uri("/api/v1/issues/export").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/issues/export")
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(reporterTokens)).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult export(String query) {
        return mvc.get().uri("/api/v1/issues/export" + query)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users)).exchange();
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static String lineStart(Long id) {
        return "\r\n" + id + ";";
    }

    private static String line(String body, Long id) {
        return Arrays.stream(body.split("\r\n")).filter(l -> l.startsWith(id + ";")).findFirst().orElseThrow();
    }

    // basta para as linhas sem aspas dos testes
    private static List<String> cells(String line) {
        return List.of(line.split(";", -1));
    }

    private Long newIssue(String category, double lat) {
        return reportService.create(TestReporters.ID, category, lat, -47.00, TestPhotos.jpeg()).issueId();
    }

    private Long operatorId() {
        TestOperators.bearer(auth, users);
        return users.findByEmail(TestOperators.EMAIL).orElseThrow().getId();
    }
}
