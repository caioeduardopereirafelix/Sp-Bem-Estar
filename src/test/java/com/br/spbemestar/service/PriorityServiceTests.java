package com.br.spbemestar.service;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PriorityServiceTests {

    @Autowired ReportService reportService;
    @Autowired PriorityService priorityService;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;

    @Test
    void priorityGrowsLogarithmicallyWithPeople() {
        Long issueId = report(UUID.randomUUID(), "POTHOLE", -21.00, -45.00);
        // buraco: peso 1,5 × ln(1 + 1 pessoa)
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(2), within(0.001));

        report(UUID.randomUUID(), "POTHOLE", -21.00, -45.00);
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(3), within(0.001));
        assertThat(column(issueId, "reporter_count")).isEqualTo(2);
    }

    @Test
    void reportingTheSameProblemAgainDoesNotRaiseThePriority() {
        UUID insistent = UUID.randomUUID();
        Long issueId = report(insistent, "POTHOLE", -21.30, -45.00);
        report(insistent, "POTHOLE", -21.30, -45.00);
        report(insistent, "POTHOLE", -21.30, -45.00);

        assertThat(column(issueId, "report_count")).isEqualTo(3);
        assertThat(column(issueId, "reporter_count")).isOne();
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(2), within(0.001));

        // outra pessoa, sim, sobe
        report(UUID.randomUUID(), "POTHOLE", -21.30, -45.00);
        assertThat(column(issueId, "reporter_count")).isEqualTo(2);
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(3), within(0.001));
    }

    @Test
    void reportsWithoutAuthorCountOneEach() {
        UUID someone = UUID.randomUUID();
        Long issueId = report(someone, "POTHOLE", -21.40, -45.00);
        report(someone, "POTHOLE", -21.40, -45.00);
        // relatos anteriores à identidade anônima não têm autor: não dá para saber se são da mesma pessoa
        jdbc.update("UPDATE report SET reporter_hash = NULL WHERE issue_id = ?", issueId);

        priorityService.recalculateOpenIssues();

        assertThat(column(issueId, "reporter_count")).isEqualTo(2);
    }

    @Test
    void rejectedReportsDoNotCount() {
        Long issueId = report(UUID.randomUUID(), "POTHOLE", -21.50, -45.00);
        report(UUID.randomUUID(), "POTHOLE", -21.50, -45.00);
        jdbc.update("""
                UPDATE report SET moderation_status = 'REJECTED', moderated_at = now(),
                       moderated_by = ?
                WHERE id = (SELECT max(id) FROM report WHERE issue_id = ?)
                """, operatorId(), issueId);

        priorityService.recalculateOpenIssues();

        assertThat(column(issueId, "reporter_count")).isOne();
    }

    @Test
    void priorityGrowsWithAge() {
        Long issueId = report("TRASH", -21.10, -45.00);
        jdbc.update("UPDATE issue SET first_reported_at = now() - interval '30 days' WHERE id = ?", issueId);

        priorityService.recalculateOpenIssues();

        // lixo: peso 1,0 × ln(2) × (1 + 30 dias / 30)
        assertThat(priority(issueId)).isCloseTo(Math.log(2) * 2, within(0.001));
    }

    @Test
    void resolvedIssuesAreNotRecalculated() {
        Long issueId = report("POTHOLE", -21.20, -45.00);
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now(), priority_score = 0 WHERE id = ?",
                issueId);

        priorityService.recalculateOpenIssues();

        assertThat(priority(issueId)).isZero();
    }

    private Long report(String category, double lat, double lon) {
        return report(TestReporters.ID, category, lat, lon);
    }

    private Long report(UUID reporter, String category, double lat, double lon) {
        return reportService.create(reporter, category, lat, lon, TestPhotos.jpeg()).issueId();
    }

    private Long operatorId() {
        TestOperators.bearer(auth, users);
        return users.findByEmail(TestOperators.EMAIL).orElseThrow().getId();
    }

    private int column(Long issueId, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM issue WHERE id = ?", Integer.class, issueId);
    }

    private double priority(Long issueId) {
        return jdbc.queryForObject("SELECT priority_score FROM issue WHERE id = ?", Double.class, issueId);
    }
}
