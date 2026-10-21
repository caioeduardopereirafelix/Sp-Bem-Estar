package com.br.spbemestar.controller;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IssueControllerTests {

    // Região própria (Rio de Janeiro), longe dos pontos usados nos outros testes
    static final double LAT = -22.9000;
    static final double LON = -43.2000;

    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;

    @Test
    void returnsGeoJsonOfIssuesInsideBoundingBox() {
        double lat = LAT;
        Long pothole = report("POTHOLE", lat, LON).issueId();
        Long trash = report("TRASH", lat + 0.001, LON + 0.001).issueId();
        Long outside = report("POTHOLE", lat + 0.05, LON).issueId();

        JsonNode body = getJson("/api/v1/issues?bbox=" + bbox(lat, LON, 0.005));

        assertThat(body.get("type").asString()).isEqualTo("FeatureCollection");
        assertThat(featureIds(body)).contains(pothole, trash).doesNotContain(outside);

        JsonNode feature = feature(body, pothole);
        assertThat(feature.get("type").asString()).isEqualTo("Feature");
        assertThat(feature.get("geometry").get("type").asString()).isEqualTo("Point");
        assertThat(feature.get("geometry").get("coordinates").get(0).asDouble()).isEqualTo(LON);
        assertThat(feature.get("geometry").get("coordinates").get(1).asDouble()).isEqualTo(lat);
        assertThat(feature.get("properties").get("category").asString()).isEqualTo("POTHOLE");
        assertThat(feature.get("properties").get("reportCount").asInt()).isEqualTo(1);
    }

    @Test
    void filtersByCategory() {
        double lat = LAT - 0.1;
        Long pothole = report("POTHOLE", lat, LON).issueId();
        Long trash = report("TRASH", lat, LON).issueId();

        JsonNode body = getJson("/api/v1/issues?category=TRASH&bbox=" + bbox(lat, LON, 0.005));

        assertThat(featureIds(body)).contains(trash).doesNotContain(pothole);
    }

    @Test
    void hidesResolvedIssuesUnlessRequested() {
        double lat = LAT - 0.2;
        Long resolved = report("POTHOLE", lat, LON).issueId();
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", resolved);
        String area = "bbox=" + bbox(lat, LON, 0.005);

        assertThat(featureIds(getJson("/api/v1/issues?" + area))).doesNotContain(resolved);
        assertThat(featureIds(getJson("/api/v1/issues?status=RESOLVED&" + area))).contains(resolved);
    }

    @Test
    void returnsIssueDetailWithReportsAndHistory() {
        double lat = LAT - 0.3;
        ReportService.ReportResult first = report("POTHOLE", lat, LON);
        report("POTHOLE", lat, LON);
        jdbc.update("""
                INSERT INTO issue_status_history (issue_id, from_status, to_status) VALUES (?, 'OPEN', 'CONFIRMED')
                """, first.issueId());

        JsonNode body = getJson("/api/v1/issues/" + first.issueId());

        assertThat(body.get("category").asString()).isEqualTo("POTHOLE");
        assertThat(body.get("latitude").asDouble()).isEqualTo(lat);
        assertThat(body.get("reportCount").asInt()).isEqualTo(2);
        assertThat(body.get("reports")).hasSize(2);
        assertThat(body.get("history").get(0).get("to").asString()).isEqualTo("CONFIRMED");

        String photoUrl = body.get("reports").get(0).get("photoUrl").asString();
        MvcTestResult photo = mvc.get().uri(photoUrl).exchange();
        assertThat(photo).hasStatusOk().hasContentType(MediaType.IMAGE_JPEG);
        assertThat(photo.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("immutable");
    }

    @Test
    void returns404ForUnknownIssueOrPhoto() {
        assertThat(mvc.get().uri("/api/v1/issues/999999999").exchange())
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.title").isEqualTo("Não encontrado");
        assertThat(mvc.get().uri("/api/v1/photos/000000000000000000000000").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsInvalidBoundingBox() {
        assertThat(mvc.get().uri("/api/v1/issues?bbox=1,2,3").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Área inválida");
        assertThat(mvc.get().uri("/api/v1/issues?bbox=-43,-22,-44,-23").exchange())
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsInvalidStatusAndLimit() {
        assertThat(mvc.get().uri("/api/v1/issues?status=FIXED").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.get().uri("/api/v1/issues?limit=5000").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private ReportService.ReportResult report(String category, double lat, double lon) {
        return reportService.create(TestReporters.ID, category, lat, lon, TestPhotos.jpeg());
    }

    private JsonNode getJson(String uri) {
        MvcTestResult result = mvc.get().uri(uri).exchange();
        assertThat(result).hasStatusOk();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String bbox(double lat, double lon, double delta) {
        return (lon - delta) + "," + (lat - delta) + "," + (lon + delta) + "," + (lat + delta);
    }

    private static List<Long> featureIds(JsonNode collection) {
        List<Long> ids = new ArrayList<>();
        collection.get("features").forEach(f -> ids.add(f.get("id").asLong()));
        return ids;
    }

    private static JsonNode feature(JsonNode collection, Long id) {
        for (JsonNode f : collection.get("features")) {
            if (f.get("id").asLong() == id) {
                return f;
            }
        }
        throw new AssertionError("Feature " + id + " não encontrada");
    }
}
