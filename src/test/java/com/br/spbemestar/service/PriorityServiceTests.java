package com.br.spbemestar.service;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PriorityServiceTests {

    @Autowired ReportService reportService;
    @Autowired PriorityService priorityService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void priorityGrowsLogarithmicallyWithReports() {
        Long issueId = report("POTHOLE", -21.00, -45.00);
        // buraco: peso 1,5 × ln(1 + 1 relato)
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(2), within(0.001));

        report("POTHOLE", -21.00, -45.00);
        assertThat(priority(issueId)).isCloseTo(1.5 * Math.log(3), within(0.001));
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
        return reportService.create(category, lat, lon, TestPhotos.jpeg()).issueId();
    }

    private double priority(Long issueId) {
        return jdbc.queryForObject("SELECT priority_score FROM issue WHERE id = ?", Double.class, issueId);
    }
}
