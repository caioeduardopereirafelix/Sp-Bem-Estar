package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ClassificationService;
import com.br.spbemestar.service.PhotoStorage;
import com.br.spbemestar.service.ReportService;
import com.jayway.jsonpath.JsonPath;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModerationTests {

    // Cada teste usa uma região própria para não agrupar relatos com os de outros testes
    static final double LON = -48.50;

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;
    @Autowired ClassificationService classification;
    @Autowired PhotoStorage photoStorage;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void listsOnlyFlaggedReportsWaitingForADecision() {
        Long mismatch = report(UUID.randomUUID(), -28.00);
        classification.apply(mismatch, "TRASH", 0.95);
        Long lowConfidence = report(UUID.randomUUID(), -28.01);
        classification.apply(lowConfidence, "POTHOLE", 0.30);
        Long agreed = report(UUID.randomUUID(), -28.02);
        classification.apply(agreed, "POTHOLE", 0.97);
        Long notClassified = report(UUID.randomUUID(), -28.03);

        List<Integer> ids = pendingIds();
        assertThat(ids).containsSubsequence(mismatch.intValue(), lowConfidence.intValue()) // mais antigo primeiro
                .doesNotContain(agreed.intValue(), notClassified.intValue());

        Map<String, Object> item = pendingItem(mismatch);
        assertThat(item).containsEntry("category", "POTHOLE").containsEntry("predictedCategory", "TRASH")
                .containsEntry("classificationStatus", "MISMATCH").containsEntry("reporterBlocked", false);
        assertThat(new BigDecimal(item.get("confidence").toString())).isEqualByComparingTo("0.95");
        assertThat(item.get("predictedCategoryName")).isNotNull();
        assertThat((String) item.get("photoUrl")).startsWith("/api/v1/photos/");
    }

    @Test
    void approvedReportKeepsCountingAndLeavesTheQueue() {
        Long reportId = flagged(UUID.randomUUID(), -28.10);
        Long issueId = issueOf(reportId);

        assertThat(decide(reportId, "APPROVED", false)).hasStatusOk()
                .bodyJson().extractingPath("$.issueReportCount").isEqualTo(1);

        assertThat(pendingIds()).doesNotContain(reportId.intValue());
        assertThat(jdbc.queryForObject("SELECT status FROM issue WHERE id = ?", String.class, issueId)).isEqualTo("OPEN");
        assertThat(decide(reportId, "REJECTED", false)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Relato já moderado");
    }

    @Test
    void rejectedReportLeavesTheIssueAndItsPhotoIsDeleted() {
        Long valid = report(UUID.randomUUID(), -28.20);
        Long spam = flagged(UUID.randomUUID(), -28.20);
        Long issueId = issueOf(valid);
        assertThat(issueOf(spam)).isEqualTo(issueId);
        String spamPhoto = jdbc.queryForObject("SELECT photo_id FROM report WHERE id = ?", String.class, spam);
        BigDecimal priorityBefore = priorityOf(issueId);

        assertThat(decide(spam, "REJECTED", false)).hasStatusOk()
                .bodyJson().extractingPath("$.issueStatus").isEqualTo("OPEN");

        assertThat(jdbc.queryForObject("SELECT report_count FROM issue WHERE id = ?", Integer.class, issueId)).isOne();
        assertThat(priorityOf(issueId)).isLessThan(priorityBefore);
        assertThat(photoStorage.load(spamPhoto)).isEmpty();
        MvcTestResult detail = mvc.get().uri("/api/v1/issues/{id}", issueId).exchange();
        List<Integer> visible = JsonPath.read(body(detail), "$.reports[*].id");
        assertThat(visible).containsExactly(valid.intValue());
    }

    @Test
    void issueWithoutValidReportsIsDiscarded() {
        Long spam = flagged(UUID.randomUUID(), -28.30);
        Long issueId = issueOf(spam);

        assertThat(decide(spam, "REJECTED", false)).hasStatusOk()
                .bodyJson().extractingPath("$.issueStatus").isEqualTo("DISCARDED");

        assertThat(jdbc.queryForList("SELECT from_status || '->' || to_status FROM issue_status_history WHERE issue_id = ?",
                String.class, issueId)).contains("OPEN->DISCARDED");
        // o histórico registra qual operador descartou
        assertThat(jdbc.queryForObject("""
                SELECT changed_by FROM issue_status_history WHERE issue_id = ? AND to_status = 'DISCARDED'
                """, Long.class, issueId)).isEqualTo(users.findByEmail(TestOperators.EMAIL).orElseThrow().getId());
        assertThat(mvc.get().uri("/api/v1/issues/{id}", issueId).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        // nenhum operador muda o status de um chamado descartado
        assertThat(mvc.patch().uri("/api/v1/issues/{id}/status", issueId)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"OPEN\"}").exchange())
                .hasStatus(HttpStatus.CONFLICT);
        // um relato novo no mesmo lugar abre outro chamado em vez de cair no descartado
        Long newIssue = issueOf(report(UUID.randomUUID(), -28.30));
        assertThat(newIssue).isNotEqualTo(issueId);
        // e só o novo aparece no mapa
        List<Integer> onMap = JsonPath.read(body(mvc.get()
                .uri("/api/v1/issues?bbox=" + (LON - 0.01) + ",-28.31," + (LON + 0.01) + ",-28.29").exchange()), "$.features[*].id");
        assertThat(onMap).contains(newIssue.intValue()).doesNotContain(issueId.intValue());
    }

    @Test
    void rejectingCanAlsoBlockTheReporter() {
        UUID spammer = UUID.randomUUID();
        Long spam = flagged(spammer, -28.40);

        assertThat(decide(spam, "REJECTED", true)).hasStatusOk()
                .bodyJson().extractingPath("$.reporterBlocked").isEqualTo(true);

        assertThatThrownBy(() -> report(spammer, -28.41)).isInstanceOf(ReporterBlockedException.class);
    }

    @Test
    void rejectsInvalidDecisions() {
        Long reportId = flagged(UUID.randomUUID(), -28.50);

        // bloquear o autor só faz sentido ao rejeitar
        assertThat(decide(reportId, "APPROVED", true)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/v1/moderation/reports/{id}", reportId)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(decide(999_999L, "APPROVED", false)).hasStatus(HttpStatus.NOT_FOUND);

        jdbc.update("UPDATE report SET reporter_hash = NULL WHERE id = ?", reportId);
        assertThat(decide(reportId, "REJECTED", true)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Relato sem autor");
        // nada foi gravado: o relato continua esperando
        assertThat(pendingIds()).contains(reportId.intValue());
    }

    private Long report(UUID reporter, double latitude) {
        return reportService.create(reporter, "POTHOLE", latitude, LON, TestPhotos.jpeg()).reportId();
    }

    private Long flagged(UUID reporter, double latitude) {
        Long reportId = report(reporter, latitude);
        classification.apply(reportId, "TRASH", 0.95);
        return reportId;
    }

    private Long issueOf(Long reportId) {
        return jdbc.queryForObject("SELECT issue_id FROM report WHERE id = ?", Long.class, reportId);
    }

    private BigDecimal priorityOf(Long issueId) {
        return jdbc.queryForObject("SELECT priority_score FROM issue WHERE id = ?", BigDecimal.class, issueId);
    }

    private MvcTestResult decide(Long reportId, String decision, boolean blockReporter) {
        return mvc.post().uri("/api/v1/moderation/reports/{id}", reportId)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"%s\", \"blockReporter\": %s}".formatted(decision, blockReporter))
                .exchange();
    }

    private MvcTestResult pending() {
        MvcTestResult result = mvc.get().uri("/api/v1/moderation/reports?size=100")
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users)).exchange();
        assertThat(result).hasStatusOk();
        return result;
    }

    private List<Integer> pendingIds() {
        return JsonPath.read(body(pending()), "$.items[*].reportId");
    }

    private Map<String, Object> pendingItem(Long reportId) {
        List<Map<String, Object>> items = JsonPath.read(body(pending()), "$.items[?(@.reportId == " + reportId + ")]");
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
