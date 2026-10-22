package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IssueStatusControllerTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;
    @Autowired IssueRepository issues;
    @Autowired TransactionTemplate transaction;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void movesIssueThroughLifecycleAndRecordsHistory() {
        Long id = newIssue(-24.00);

        assertThat(changeStatus(id, "CONFIRMED")).hasStatusOk()
                .bodyJson().extractingPath("$.from").isEqualTo("OPEN");
        assertThat(changeStatus(id, "IN_PROGRESS")).hasStatusOk();
        MvcTestResult resolved = changeStatus(id, "RESOLVED");

        assertThat(resolved).hasStatusOk().bodyJson().extractingPath("$.resolvedAt").isNotNull();
        assertThat(jdbc.queryForList("""
                SELECT from_status || '->' || to_status FROM issue_status_history
                WHERE issue_id = ? ORDER BY changed_at, id
                """, String.class, id))
                .containsExactly("OPEN->CONFIRMED", "CONFIRMED->IN_PROGRESS", "IN_PROGRESS->RESOLVED");
        assertThat(jdbc.queryForObject("SELECT status FROM issue WHERE id = ?", String.class, id))
                .isEqualTo("RESOLVED");
    }

    @Test
    void recordsStatusChangeEventInOutbox() {
        Long id = newIssue(-24.01);

        changeStatus(id, "RESOLVED");

        assertThat(jdbc.queryForObject("""
                SELECT payload ->> 'to' FROM outbox_event
                WHERE aggregate_id = ? AND type = 'IssueStatusChanged'
                """, String.class, id)).isEqualTo("RESOLVED");
    }

    @Test
    void rejectsInvalidTransitionWithConflict() {
        Long id = newIssue(-24.02);
        changeStatus(id, "RESOLVED");

        assertThat(changeStatus(id, "OPEN"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Transição de status inválida");
        assertThat(changeStatus(id, "RESOLVED")).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsInvalidRequests() {
        Long id = newIssue(-24.03);

        assertThat(changeStatus(id, "FIXED")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.patch().uri("/api/v1/issues/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                .contentType(MediaType.APPLICATION_JSON).content("{}").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(changeStatus(999_999_999L, "CONFIRMED")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void recordsWhichOperatorChangedTheStatus() {
        Long id = newIssue(-24.05);
        String operator = TestOperators.bearer(auth, users);
        Long operatorId = users.findByEmail(TestOperators.EMAIL).orElseThrow().getId();

        assertThat(changeStatus(id, "CONFIRMED")).hasStatusOk();

        assertThat(jdbc.queryForObject("SELECT changed_by FROM issue_status_history WHERE issue_id = ?",
                Long.class, id)).isEqualTo(operatorId);
        assertThat(jdbc.queryForObject("""
                SELECT payload ->> 'changedBy' FROM outbox_event
                WHERE aggregate_id = ? AND type = 'IssueStatusChanged'
                """, String.class, id)).isEqualTo(operatorId.toString());

        // o operador vê o nome de quem mudou; o mapa público vê o histórico sem nomes
        assertThat(mvc.get().uri("/api/v1/issues/{id}", id).header(HttpHeaders.AUTHORIZATION, operator).exchange())
                .bodyJson().extractingPath("$.history[0].changedBy").isEqualTo("Operador de teste");
        assertThat(mvc.get().uri("/api/v1/issues/{id}", id).exchange())
                .bodyJson().extractingPath("$.history[0].changedBy").isNull();
    }

    @Test
    void detectsConcurrentModificationThroughVersion() {
        Long id = newIssue(-24.04);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Issue issue = issues.findById(id).orElseThrow();
            // Outra transação altera o chamado depois que ele foi lido
            jdbc.update("UPDATE issue SET version = version + 1 WHERE id = ?", id);
            issue.changeStatus(IssueStatus.CONFIRMED, Instant.now(), null);
            issues.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForObject("SELECT status FROM issue WHERE id = ?", String.class, id))
                .isEqualTo("OPEN");
    }

    private Long newIssue(double lat) {
        return reportService.create(TestReporters.ID, "POTHOLE", lat, -47.00, TestPhotos.jpeg()).issueId();
    }

    private MvcTestResult changeStatus(Long id, String status) {
        return mvc.patch().uri("/api/v1/issues/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"" + status + "\"}")
                .exchange();
    }
}
