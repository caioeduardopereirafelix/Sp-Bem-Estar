package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.CitizenAccountService;
import com.br.spbemestar.service.ReporterTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IssueResolutionTests {

    @Autowired MockMvcTester mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReportService reportService;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired CitizenAccountService citizens;
    @Autowired ReporterJwt reporterJwt;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired GridFsTemplate gridFs;

    @Test
    void resolvesWithThePhotoAndNoteShownOnTheMap() {
        Long id = newIssue(-26.00);

        MvcTestResult result = resolve(id, photo(TestPhotos.jpeg()), "  Buraco tapado com asfalto\nnovo.  ");

        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.to").isEqualTo("RESOLVED");
        assertThat(result).bodyJson().extractingPath("$.resolvedAt").isNotNull();
        MvcTestResult detail = mvc.get().uri("/api/v1/issues/{id}", id).exchange();
        assertThat(detail).bodyJson().extractingPath("$.status").isEqualTo("RESOLVED");
        // quebra de linha vira espaço, e as pontas saem
        assertThat(detail).bodyJson().extractingPath("$.resolution.note").isEqualTo("Buraco tapado com asfalto novo.");
        String photoUrl = jdbc.queryForObject("SELECT '/api/v1/photos/' || resolution_photo_id FROM issue WHERE id = ?",
                String.class, id);
        assertThat(detail).bodyJson().extractingPath("$.resolution.photoUrl").isEqualTo(photoUrl);
        // a foto do conserto é pública, como a dos relatos
        assertThat(mvc.get().uri(photoUrl).exchange()).hasStatusOk().hasContentType("image/jpeg");
        assertThat(jdbc.queryForObject("""
                SELECT payload ->> 'to' FROM outbox_event WHERE aggregate_id = ? AND type = 'IssueStatusChanged'
                """, String.class, id)).isEqualTo("RESOLVED");
    }

    @Test
    void photoAndNoteAreOptional() {
        Long id = newIssue(-26.01);

        assertThat(mvc.post().uri("/api/v1/issues/{id}/resolution", id).multipart()
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users)).exchange()).hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/issues/{id}", id).exchange())
                .bodyJson().extractingPath("$.resolution").isNull();
        assertThat(status(id)).isEqualTo("RESOLVED");
    }

    @Test
    void rejectsTooLongNoteWithoutStoringThePhoto() {
        Long id = newIssue(-26.02);
        int photosBefore = storedPhotos();

        assertThat(resolve(id, photo(TestPhotos.jpeg()), "a".repeat(501)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Resolução inválida");

        assertThat(status(id)).isEqualTo("OPEN");
        assertThat(storedPhotos()).isEqualTo(photosBefore);
    }

    @Test
    void rejectsSomethingThatIsNotAPhoto() {
        Long id = newIssue(-26.03);

        assertThat(resolve(id, photo("não é uma imagem".getBytes(StandardCharsets.UTF_8)), null))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Foto inválida");
        assertThat(status(id)).isEqualTo("OPEN");
    }

    @Test
    void alreadyResolvedIssueKeepsItsResolutionAndDropsTheNewPhoto() {
        Long id = newIssue(-26.04);
        assertThat(resolve(id, null, "Primeira resolução")).hasStatusOk();
        int photosBefore = storedPhotos();

        assertThat(resolve(id, photo(TestPhotos.jpeg()), "Segunda")).hasStatus(HttpStatus.CONFLICT);

        // a foto gravada antes da transação é apagada quando a mudança de status falha
        assertThat(storedPhotos()).isEqualTo(photosBefore);
        assertThat(jdbc.queryForObject("SELECT resolution_note FROM issue WHERE id = ?", String.class, id))
                .isEqualTo("Primeira resolução");
    }

    @Test
    void onlyOperatorsResolve() {
        Long id = newIssue(-26.05);

        assertThat(mvc.post().uri("/api/v1/issues/{id}/resolution", id).multipart().exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/issues/{id}/resolution", id).multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(citizens)).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(status(id)).isEqualTo("OPEN");
    }

    @Test
    void citizenSeesTheResolutionInTheirReports() {
        String token = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        UUID me = UUID.fromString(reporterJwt.decoder().decode(token).getSubject());
        Long id = reportService.create(me, "POTHOLE", -26.06, -47.00, TestPhotos.jpeg()).issueId();

        assertThat(resolve(id, photo(TestPhotos.jpeg()), "Tapado")).hasStatusOk();

        MvcTestResult mine = mvc.get().uri("/api/v1/reporters/me/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
        assertThat(mine).bodyJson().extractingPath("$.items[0].resolution.note").isEqualTo("Tapado");
        assertThat(mine).bodyJson().extractingPath("$.items[0].resolution.photoUrl").asString()
                .startsWith("/api/v1/photos/");
    }

    private MvcTestResult resolve(Long id, MockMultipartFile photo, String note) {
        var request = mvc.post().uri("/api/v1/issues/{id}/resolution", id).multipart()
                .header(HttpHeaders.AUTHORIZATION, TestOperators.bearer(auth, users));
        if (photo != null) {
            request = request.file(photo);
        }
        if (note != null) {
            request = request.param("note", note);
        }
        return request.exchange();
    }

    private static MockMultipartFile photo(byte[] content) {
        return new MockMultipartFile("photo", "conserto.jpg", "image/jpeg", content);
    }

    private String status(Long id) {
        return jdbc.queryForObject("SELECT status FROM issue WHERE id = ?", String.class, id);
    }

    private int storedPhotos() {
        return gridFs.find(new Query()).into(new ArrayList<>()).size();
    }

    private Long newIssue(double lat) {
        return reportService.create(TestReporters.ID, "POTHOLE", lat, -47.00, TestPhotos.jpeg()).issueId();
    }
}
