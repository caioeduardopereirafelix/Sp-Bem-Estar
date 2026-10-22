package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ClassificationService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import com.jayway.jsonpath.JsonPath;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os chamados destes testes recebem prioridade acima de 9000 para ficar no topo da fila, à frente dos
 * criados por outros testes no mesmo banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OperatorQueueTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;
    @Autowired ClassificationService classification;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void queueAndModerationAreOnlyForOperators() {
        for (String path : List.of("/api/v1/issues/queue", "/api/v1/moderation/reports")) {
            assertThat(mvc.get().uri(path).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
            // o token do cidadão não vale aqui, mesmo com GET /api/v1/** sendo público para o mapa
            assertThat(mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(reporterTokens))
                    .exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                    .exchange()).hasStatusOk();
        }
    }

    @Test
    void ordersByPriorityThenByAge() {
        Long low = issueWithPriority(-27.00, "POTHOLE", 9100);
        Long highNewer = issueWithPriority(-27.01, "POTHOLE", 9300);
        Long highOlder = issueWithPriority(-27.02, "POTHOLE", 9300);
        jdbc.update("UPDATE issue SET first_reported_at = first_reported_at - interval '3 days' WHERE id = ?", highOlder);
        Long resolved = issueWithPriority(-27.03, "POTHOLE", 9900);
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", resolved);

        List<Integer> ids = ids(queue(""));

        assertThat(ids).doesNotContain(resolved.intValue());
        assertThat(ids).containsSubsequence(highOlder.intValue(), highNewer.intValue(), low.intValue());
        assertThat(ids(queue("&status=RESOLVED"))).contains(resolved.intValue());
    }

    @Test
    void filtersByCategoryAndNeighborhood() {
        Long trash = issueWithPriority(-27.10, "TRASH", 9500);
        Long pothole = issueWithPriority(-27.11, "POTHOLE", 9500);
        Long neighborhood = jdbc.queryForObject("""
                INSERT INTO neighborhood (name, city, population, geom)
                VALUES ('Bairro da fila', 'Cidade da fila', 1000,
                        ST_Multi(ST_GeomFromText('POLYGON((-47 -27.2, -46.9 -27.2, -46.9 -27.1, -47 -27.1, -47 -27.2))', 4326)))
                RETURNING id
                """, Long.class);
        jdbc.update("UPDATE issue SET neighborhood_id = ? WHERE id = ?", neighborhood, trash);

        List<Integer> byCategory = ids(queue("&category=TRASH"));
        assertThat(byCategory).contains(trash.intValue()).doesNotContain(pothole.intValue());

        MvcTestResult byNeighborhood = queue("&neighborhoodId=" + neighborhood);
        assertThat(ids(byNeighborhood)).containsExactly(trash.intValue());
        assertThat(byNeighborhood).bodyJson().extractingPath("$.items[0].neighborhood").isEqualTo("Bairro da fila");
    }

    @Test
    void showsHowManyReportsWaitForModeration() {
        Long issueId = issueWithPriority(-27.20, "POTHOLE", 9700);
        Long flagged = reportService.create(TestReporters.ID, "POTHOLE", -27.20, -47.00, TestPhotos.jpeg()).reportId();
        classification.apply(flagged, "TRASH", 0.95); // o modelo discorda da categoria

        assertThat(itemOf(queue(""), issueId)).containsEntry("reportCount", 2).containsEntry("pendingModeration", 1);
    }

    @Test
    void paginates() {
        issueWithPriority(-27.30, "POTHOLE", 9800);
        issueWithPriority(-27.31, "POTHOLE", 9801);
        String operator = TestOperators.bearer(auth, users);

        MvcTestResult first = mvc.get().uri("/api/v1/issues/queue?size=1&page=0")
                .header(HttpHeaders.AUTHORIZATION, operator).exchange();
        MvcTestResult second = mvc.get().uri("/api/v1/issues/queue?size=1&page=1")
                .header(HttpHeaders.AUTHORIZATION, operator).exchange();

        assertThat(first).bodyJson().extractingPath("$.size").isEqualTo(1);
        assertThat(second).bodyJson().extractingPath("$.page").isEqualTo(1);
        assertThat(ids(first)).hasSize(1).doesNotContainAnyElementsOf(ids(second));
        assertThat(mvc.get().uri("/api/v1/issues/queue?size=500").header(HttpHeaders.AUTHORIZATION, operator)
                .exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private Long issueWithPriority(double latitude, String category, int priority) {
        Long issueId = reportService.create(TestReporters.ID, category, latitude, -47.00, TestPhotos.jpeg()).issueId();
        jdbc.update("UPDATE issue SET priority_score = ? WHERE id = ?", priority, issueId);
        return issueId;
    }

    private MvcTestResult queue(String query) {
        MvcTestResult result = mvc.get().uri("/api/v1/issues/queue?size=100" + query)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users)).exchange();
        assertThat(result).hasStatusOk();
        return result;
    }

    private static List<Integer> ids(MvcTestResult result) {
        return JsonPath.read(body(result), "$.items[*].id");
    }

    private static Map<String, Object> itemOf(MvcTestResult result, Long issueId) {
        List<Map<String, Object>> items = JsonPath.read(body(result), "$.items[?(@.id == " + issueId + ")]");
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
