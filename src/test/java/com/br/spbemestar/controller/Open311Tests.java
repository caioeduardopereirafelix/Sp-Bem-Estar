package com.br.spbemestar.controller;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.privacy.controller=Prefeitura de Teste")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class Open311Tests {

    @Autowired MockMvcTester mvc;
    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void servicesListTheActiveCategories() {
        MvcTestResult result = mvc.get().uri("/open311/v2/services.json").exchange();

        assertThat(result).hasStatusOk().hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
        assertThat(result).bodyJson().extractingPath("$[*].service_code").asArray().contains("POTHOLE", "TRASH");
        assertThat(result).bodyJson().extractingPath("$[0].metadata").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$[0].type").isEqualTo("realtime");
        assertThat(result).bodyJson().extractingPath("$[0].service_name").isNotNull();
    }

    @Test
    void aRequestFollowsTheGeoReportFormat() {
        Long id = newIssue("POTHOLE", -28.10);

        MvcTestResult result = mvc.get().uri("/open311/v2/requests/{id}.json", id).exchange();

        assertThat(result).hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                .headers().hasValue(HttpHeaders.CACHE_CONTROL, "max-age=60, public");
        var body = assertThat(result).bodyJson();
        body.extractingPath("$[0].service_request_id").isEqualTo(String.valueOf(id));
        body.extractingPath("$[0].status").isEqualTo("open");
        body.extractingPath("$[0].status_notes").isEqualTo("Aberto");
        body.extractingPath("$[0].service_code").isEqualTo("POTHOLE");
        body.extractingPath("$[0].agency_responsible").isEqualTo("Prefeitura de Teste");
        body.extractingPath("$[0].description").isEqualTo("Relatado por 1 pessoa.");
        body.extractingPath("$[0].lat").isEqualTo(-28.10);
        body.extractingPath("$[0].long").isEqualTo(-48.50);
        body.extractingPath("$[0].requested_datetime").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}-03:00");
        body.extractingPath("$[0].media_url").asString().startsWith("http://localhost/api/v1/photos/");
    }

    @Test
    void nothingAboutWhoReportedLeaks() throws Exception {
        Long id = newIssue("TRASH", -28.11);

        String body = mvc.get().uri("/open311/v2/requests.json?service_request_id={id}", id).exchange()
                .getResponse().getContentAsString();

        Map<String, Object> request = JsonPath.<List<Map<String, Object>>>read(body, "$").get(0);
        assertThat(request).doesNotContainKeys("reporter_hash", "email", "reporter_id", "priority_score");
        String hash = jdbc.queryForObject("SELECT reporter_hash FROM report WHERE issue_id = ? LIMIT 1", String.class, id);
        assertThat(body).doesNotContain(hash).doesNotContain(TestReporters.ID.toString());
    }

    @Test
    void resolvedBecomesClosedWithTheNoteAndTheRepairPhoto() {
        Long id = newIssue("POTHOLE", -28.12);
        jdbc.update("""
                UPDATE issue SET status = 'RESOLVED', resolved_at = now(), resolution_note = 'Buraco tapado',
                       resolution_photo_id = 'foto-do-conserto' WHERE id = ?
                """, id);

        MvcTestResult result = mvc.get().uri("/open311/v2/requests/{id}.json", id).exchange();

        assertThat(result).bodyJson().extractingPath("$[0].status").isEqualTo("closed");
        assertThat(result).bodyJson().extractingPath("$[0].status_notes").isEqualTo("Resolvido: Buraco tapado");
        assertThat(result).bodyJson().extractingPath("$[0].media_url")
                .isEqualTo("http://localhost/api/v1/photos/foto-do-conserto");
    }

    @Test
    void discardedIssuesAndPhotosUnderModerationStayOut() {
        Long discarded = newIssue("TRASH", -28.13);
        jdbc.update("UPDATE issue SET status = 'DISCARDED' WHERE id = ?", discarded);
        Long underModeration = newIssue("TRASH", -28.14);
        jdbc.update("UPDATE report SET classification_status = 'MISMATCH' WHERE issue_id = ?", underModeration);

        assertThat(mvc.get().uri("/open311/v2/requests/{id}.json", discarded).exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$[0].code").isEqualTo(404);
        assertThat(mvc.get().uri("/open311/v2/requests.json?service_request_id={id}", discarded).exchange())
                .hasStatusOk().bodyJson().extractingPath("$").asArray().isEmpty();
        assertThat(mvc.get().uri("/open311/v2/requests/{id}.json", underModeration).exchange())
                .hasStatusOk().bodyJson().doesNotHavePath("$[0].media_url");
    }

    @Test
    void filtersByCodeStatusAndDate() {
        Long pothole = newIssue("POTHOLE", -28.15);
        Long trash = newIssue("TRASH", -28.16);
        Long old = newIssue("POTHOLE", -28.17);
        jdbc.update("UPDATE issue SET first_reported_at = now() - interval '200 days' WHERE id = ?", old);
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", trash);

        assertThat(ids("/open311/v2/requests.json?service_code=POTHOLE")).contains(pothole).doesNotContain(trash, old);
        assertThat(ids("/open311/v2/requests.json?status=closed")).contains(trash).doesNotContain(pothole);
        assertThat(ids("/open311/v2/requests.json?status=open,closed")).contains(pothole, trash);
        assertThat(ids("/open311/v2/requests.json?start_date=2020-01-01&service_code=POTHOLE")).contains(old, pothole);
        assertThat(ids("/open311/v2/requests.json?service_request_id=" + pothole + "," + trash))
                .containsExactlyInAnyOrder(pothole, trash);
    }

    @Test
    void badParametersAnswerInTheOpen311ErrorFormat() {
        for (String query : List.of("status=pendente", "start_date=ontem", "service_request_id=abc",
                "start_date=2026-02-01&end_date=2026-01-01")) {
            assertThat(mvc.get().uri("/open311/v2/requests.json?" + query).exchange())
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson().extractingPath("$[0].code").isEqualTo(400);
        }
        assertThat(mvc.get().uri("/open311/v2/requests/abc.json").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private List<Long> ids(String uri) {
        try {
            List<String> ids = JsonPath.read(mvc.get().uri(uri).exchange().getResponse().getContentAsString(),
                    "$[*].service_request_id");
            return ids.stream().map(Long::valueOf).toList();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long newIssue(String category, double lat) {
        return reportService.create(TestReporters.ID, category, lat, -48.50, TestPhotos.jpeg()).issueId();
    }
}
