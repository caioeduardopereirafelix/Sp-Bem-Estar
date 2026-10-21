package com.br.spbemestar.controller;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.dto.ReportResponse;
import com.br.spbemestar.service.PhotoStorage;
import com.br.spbemestar.service.ReporterTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReportControllerTests {

    // Cada teste usa uma região própria (~1 km de distância) para não interferir nos outros
    static final double BASE_LAT = -23.5000;
    static final double BASE_LON = -46.6000;
    static final double TEN_METERS = 0.00009;

    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PhotoStorage photoStorage;
    @Autowired ReporterTokenService reporterTokens;

    @Test
    void firstReportOpensNewIssue() {
        ReportResponse response = createReport("POTHOLE", BASE_LAT, BASE_LON);

        assertThat(response.newIssue()).isTrue();
        assertThat(response.issueReportCount()).isEqualTo(1);
        assertThat(response.issueStatus()).isEqualTo("OPEN");
    }

    @Test
    void nearbyReportOfSameCategoryJoinsExistingIssue() {
        double lat = BASE_LAT - 0.01;
        ReportResponse first = createReport("POTHOLE", lat, BASE_LON);

        ReportResponse second = createReport("POTHOLE", lat + TEN_METERS, BASE_LON);

        assertThat(second.newIssue()).isFalse();
        assertThat(second.issueId()).isEqualTo(first.issueId());
        assertThat(second.issueReportCount()).isEqualTo(2);
    }

    @Test
    void reportOutsideRadiusOpensAnotherIssue() {
        double lat = BASE_LAT - 0.02;
        ReportResponse first = createReport("POTHOLE", lat, BASE_LON);

        // Buraco: raio de 15 m. Este ponto está a ~50 m
        ReportResponse far = createReport("POTHOLE", lat + 5 * TEN_METERS, BASE_LON);

        assertThat(far.newIssue()).isTrue();
        assertThat(far.issueId()).isNotEqualTo(first.issueId());
    }

    @Test
    void sameSpotDifferentCategoryOpensAnotherIssue() {
        double lat = BASE_LAT - 0.03;
        ReportResponse pothole = createReport("POTHOLE", lat, BASE_LON);

        ReportResponse trash = createReport("TRASH", lat, BASE_LON);

        assertThat(trash.newIssue()).isTrue();
        assertThat(trash.issueId()).isNotEqualTo(pothole.issueId());
    }

    @Test
    void resolvedIssueDoesNotReceiveNewReports() {
        double lat = BASE_LAT - 0.04;
        ReportResponse first = createReport("POTHOLE", lat, BASE_LON);
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", first.issueId());

        ReportResponse reopened = createReport("POTHOLE", lat, BASE_LON);

        assertThat(reopened.newIssue()).isTrue();
        assertThat(reopened.issueId()).isNotEqualTo(first.issueId());
    }

    @Test
    void storesSanitizedPhotoAndOutboxEvent() {
        ReportResponse response = createReport("TRASH", BASE_LAT - 0.05, BASE_LON);

        String photoId = jdbc.queryForObject("SELECT photo_id FROM report WHERE id = ?", String.class,
                response.reportId());
        assertThat(photoStorage.load(photoId))
                .hasValueSatisfying(photo -> assertThat(photo.contentType()).isEqualTo("image/jpeg"));

        String payloadIssueId = jdbc.queryForObject("""
                SELECT payload ->> 'issueId' FROM outbox_event
                WHERE aggregate_id = ? AND type = 'ReportCreated' AND published_at IS NULL
                """, String.class, response.reportId());
        assertThat(payloadIssueId).isEqualTo(response.issueId().toString());
    }

    @Test
    void rejectsInvalidCoordinates() {
        MvcTestResult result = post(photo(TestPhotos.jpeg()), "POTHOLE", "95", "-46.6");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.latitude").isNotNull();
    }

    @Test
    void rejectsUnknownCategory() {
        MvcTestResult result = post(photo(TestPhotos.jpeg()), "VOLCANO", "-23.5", "-46.6");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Categoria inválida");
    }

    @Test
    void rejectsFileThatIsNotAnImage() {
        byte[] fake = "isto nao e uma imagem".getBytes(StandardCharsets.UTF_8);

        MvcTestResult result = post(photo(fake), "POTHOLE", "-23.5", "-46.6");

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Foto inválida");
    }

    @Test
    void rejectsRequestWithoutPhoto() {
        MvcTestResult result = mvc.post().uri("/api/v1/reports")
                .multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(reporterTokens))
                .param("latitude", "-23.5")
                .param("longitude", "-46.6")
                .param("category", "POTHOLE")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    }

    private ReportResponse createReport(String category, double latitude, double longitude) {
        MvcTestResult result = post(photo(TestPhotos.jpeg()), category,
                String.valueOf(latitude), String.valueOf(longitude));
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return json.readValue(result.getResponse().getContentAsByteArray(), ReportResponse.class);
    }

    private MvcTestResult post(MockMultipartFile photo, String category, String latitude, String longitude) {
        return mvc.post().uri("/api/v1/reports")
                .multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(reporterTokens))
                .file(photo)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .param("category", category)
                .exchange();
    }

    private static MockMultipartFile photo(byte[] content) {
        return new MockMultipartFile("photo", "foto.jpg", "image/jpeg", content);
    }
}
