package com.br.spbemestar.controller;

import com.br.spbemestar.TestLogin;
import com.br.spbemestar.TestMailbox;
import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.config.ReporterProperties;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.dto.ReporterTokenResponse;
import com.br.spbemestar.model.ReporterRefreshToken;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.ReporterRefreshTokenRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReporterHasher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReporterTokenTests {

    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReporterJwt reporterJwt;
    @Autowired JwtEncoder operatorEncoder;
    @Autowired ReporterHasher hasher;
    @Autowired ReporterProperties reporterProperties;
    @Autowired SecurityProperties securityProperties;
    @Autowired ReporterRefreshTokenRepository refreshTokens;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired TestMailbox mailbox;

    @Test
    void reportStoresOnlyTheHmacOfTheReporter() {
        ReporterTokenResponse tokens = register();
        String reporterId = reporterJwt.decoder().decode(tokens.accessToken()).getSubject();

        Long reportId = createReport(tokens.accessToken(), -26.00);

        String stored = jdbc.queryForObject("SELECT reporter_hash FROM report WHERE id = ?", String.class, reportId);
        assertThat(stored).hasSize(64).isEqualTo(hasher.reporterHash(UUID.fromString(reporterId)))
                .doesNotContain(reporterId);
        // o refresh token também não é gravado, só o SHA-256 dele
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reporter_refresh_token WHERE token_hash = ?",
                Integer.class, ReporterHasher.tokenHash(tokens.refreshToken()))).isOne();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reporter_refresh_token WHERE token_hash = ?",
                Integer.class, tokens.refreshToken())).isZero();
    }

    @Test
    void sameReporterKeepsTheSameHashAfterRefresh() {
        ReporterTokenResponse first = register();
        ReporterTokenResponse renewed = refresh(first.refreshToken());
        assertThat(renewed.refreshToken()).isNotEqualTo(first.refreshToken());

        Long before = createReport(first.accessToken(), -26.01);
        Long after = createReport(renewed.accessToken(), -26.02);
        Long otherPerson = createReport(register().accessToken(), -26.03);

        assertThat(hashOf(after)).isEqualTo(hashOf(before));
        assertThat(hashOf(otherPerson)).isNotEqualTo(hashOf(before));
    }

    @Test
    void reportRequiresAValidReporterToken() {
        assertThat(postReport(null, -26.10)).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Não autenticado");
        assertThat(postReport("Bearer nao-e-um-jwt", -26.10)).hasStatus(HttpStatus.UNAUTHORIZED);

        // o token do operador não serve para enviar relato
        assertThat(postReport(TestOperators.bearer(auth, users), -26.10)).hasStatus(HttpStatus.UNAUTHORIZED);

        Instant now = Instant.now();
        UUID someone = UUID.randomUUID();
        // vencido
        assertThat(postReport(sign(reporterJwt.encoder(), someone, List.of(reporterProperties.audience()),
                now.minus(Duration.ofHours(1)), now.minus(Duration.ofMinutes(30))), -26.10))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        // assinado com a chave do operador, mesmo com o aud certo
        assertThat(postReport(sign(operatorEncoder, someone, List.of(reporterProperties.audience()),
                now, now.plusSeconds(600)), -26.10)).hasStatus(HttpStatus.UNAUTHORIZED);
        // chave certa, aud errado
        assertThat(postReport(sign(reporterJwt.encoder(), someone, List.of("outra-api"),
                now, now.plusSeconds(600)), -26.10)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reporterTokenDoesNotOpenOperatorRoutes() {
        String reporter = "Bearer " + register().accessToken();

        assertThat(mvc.patch().uri("/api/v1/issues/1/status").header(HttpHeaders.AUTHORIZATION, reporter)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"CONFIRMED\"}").exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/v1/reports/1/block-reporter").header(HttpHeaders.AUTHORIZATION, reporter)
                .exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reusedRefreshTokenRevokesTheWholeIdentity() {
        ReporterTokenResponse first = register();
        ReporterTokenResponse second = refresh(first.refreshToken());

        // o token já trocado aparece de novo: alguém tem uma cópia dele
        assertThat(postRefresh(first.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Renovação recusada");
        // o par emitido depois também deixa de valer, porque pode estar nas mãos de quem copiou
        assertThat(postRefresh(second.refreshToken())).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsUnknownExpiredAndMalformedRefreshTokens() {
        assertThat(postRefresh("token-que-nunca-existiu")).hasStatus(HttpStatus.UNAUTHORIZED);

        Instant past = Instant.now().minus(Duration.ofDays(100));
        refreshTokens.save(ReporterRefreshToken.of(UUID.randomUUID(), ReporterHasher.tokenHash("vencido"),
                past, past.plus(Duration.ofDays(90))));
        assertThat(postRefresh("vencido")).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(mvc.post().uri("/api/v1/reporters/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void operatorBlocksTheAuthorOfAReport() {
        ReporterTokenResponse spammer = register();
        Long reportId = createReport(spammer.accessToken(), -26.20);
        String operator = TestOperators.bearer(auth, users);

        assertThat(block(reportId, null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(block(reportId, operator)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(block(reportId, operator)).hasStatus(HttpStatus.NO_CONTENT); // de novo, sem erro

        // o JWT ainda vale por alguns minutos, mas o relato já é recusado
        assertThat(postReport("Bearer " + spammer.accessToken(), -26.21)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.title").isEqualTo("Autor bloqueado");
        assertThat(postRefresh(spammer.refreshToken())).hasStatus(HttpStatus.FORBIDDEN);
        // e quem não foi bloqueado continua enviando
        assertThat(postReport("Bearer " + register().accessToken(), -26.22)).hasStatus(HttpStatus.CREATED);

        assertThat(block(999_999L, operator)).hasStatus(HttpStatus.NOT_FOUND);
        Long anonymous = jdbc.queryForObject("""
                INSERT INTO report (issue_id, category_code, location, photo_id, created_at)
                SELECT issue_id, category_code, location, photo_id, now() FROM report WHERE id = ?
                RETURNING id
                """, Long.class, reportId);
        assertThat(block(anonymous, operator)).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.title").isEqualTo("Relato sem autor");
    }

    @Test
    void reporterTokensAreShortLived() {
        ReporterTokenResponse tokens = register();
        Instant issuedAt = reporterJwt.decoder().decode(tokens.accessToken()).getIssuedAt();

        assertThat(Duration.between(issuedAt, tokens.expiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(Duration.between(issuedAt, tokens.refreshTokenExpiresAt())).isEqualTo(Duration.ofDays(90));
        assertThat(reporterJwt.decoder().decode(tokens.accessToken()).getClaims()).doesNotContainKey("roles");
    }

    /** Um cidadão novo, entrando com um e-mail que nenhum outro teste usa. */
    private ReporterTokenResponse register() {
        MvcTestResult result = TestLogin.login(mvc, mailbox, TestReporters.email());
        assertThat(result).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("CITIZEN");
        return json.readValue(result.getResponse().getContentAsByteArray(), ReporterTokenResponse.class);
    }

    private ReporterTokenResponse refresh(String refreshToken) {
        MvcTestResult result = postRefresh(refreshToken);
        assertThat(result).hasStatusOk();
        return json.readValue(result.getResponse().getContentAsByteArray(), ReporterTokenResponse.class);
    }

    private MvcTestResult postRefresh(String refreshToken) {
        return mvc.post().uri("/api/v1/reporters/refresh").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("refreshToken", refreshToken))).exchange();
    }

    private Long createReport(String accessToken, double latitude) {
        MvcTestResult result = postReport("Bearer " + accessToken, latitude);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) json.readValue(result.getResponse().getContentAsByteArray(), Map.class)
                .get("reportId")).longValue();
    }

    private MvcTestResult postReport(String authorization, double latitude) {
        var request = mvc.post().uri("/api/v1/reports").multipart()
                .file("photo", TestPhotos.jpeg())
                .param("category", "TRASH").param("latitude", String.valueOf(latitude)).param("longitude", "-48.00");
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private MvcTestResult block(Long reportId, String authorization) {
        var request = mvc.post().uri("/api/v1/reports/{id}/block-reporter", reportId);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private String hashOf(Long reportId) {
        return jdbc.queryForObject("SELECT reporter_hash FROM report WHERE id = ?", String.class, reportId);
    }

    private String sign(JwtEncoder encoder, UUID subject, List<String> audience, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(securityProperties.issuer()).audience(audience).subject(subject.toString())
                .issuedAt(issuedAt).expiresAt(expiresAt)
                .build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
