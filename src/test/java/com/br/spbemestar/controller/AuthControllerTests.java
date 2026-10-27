package com.br.spbemestar.controller;

import com.br.spbemestar.TestLogin;
import com.br.spbemestar.TestMailbox;
import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.OperatorBootstrapRunner;
import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.CitizenAccountService;
import com.br.spbemestar.service.ReporterTokenService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerTests {

    @Autowired MockMvcTester mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired ReportService reportService;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired CitizenAccountService citizens;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SecurityProperties properties;
    @Autowired TestMailbox mailbox;

    @Test
    void rejectsMalformedLogin() {
        assertThat(mvc.post().uri("/api/v1/auth/code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nao-e-email\"}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/v1/auth/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"a@b.com\", \"code\": \"12ab\"}").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void statusChangeRequiresAValidOperatorToken() {
        Long id = newIssue(-25.01);

        assertThat(changeStatus(id, null)).hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.title").isEqualTo("Não autenticado");
        assertThat(changeStatus(id, "Bearer nao-e-um-jwt")).hasStatus(HttpStatus.UNAUTHORIZED);

        Instant past = Instant.now().minus(Duration.ofHours(2));
        assertThat(changeStatus(id, "Bearer " + token(jwtEncoder, properties.issuer(), List.of("OPERATOR"),
                past, past.plus(Duration.ofHours(1))))).hasStatus(HttpStatus.UNAUTHORIZED);

        JwtEncoder otherKey = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "outra-chave-com-pelo-menos-32-bytes!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        assertThat(changeStatus(id, "Bearer " + token(otherKey, properties.issuer(), List.of("OPERATOR"),
                Instant.now(), Instant.now().plusSeconds(600)))).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(changeStatus(id, "Bearer " + token(jwtEncoder, "outro-emissor", List.of("OPERATOR"),
                Instant.now(), Instant.now().plusSeconds(600)))).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(changeStatus(id, "Bearer " + token(jwtEncoder, properties.issuer(), List.of("CITIZEN"),
                Instant.now(), Instant.now().plusSeconds(600))))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.title").isEqualTo("Acesso negado");

        assertThat(changeStatus(id, TestOperators.bearer(auth, users))).hasStatusOk();
    }

    @Test
    void citizenRoutesStayPublic() {
        Long id = newIssue(-25.02);

        assertThat(mvc.get().uri("/api/v1/issues/{id}", id).exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/categories").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/privacidade.html").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/termos.html").exchange()).hasStatusOk();
        // o app instalável (PWA): o navegador só aceita o manifesto com o tipo certo
        assertThat(mvc.get().uri("/manifest.webmanifest").exchange()).hasStatusOk()
                .hasContentType("application/manifest+json");
        assertThat(mvc.get().uri("/icons/icon-192.png").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/offline.html").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/fonts/atkinson-400.woff2").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/vendor/leaflet-maplibre-gl.js").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/privacy").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.contact").isNull();
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.components.securitySchemes.bearer.scheme").isEqualTo("bearer");
        // o cidadão não faz login: o token dele é anônimo, emitido em POST /api/v1/reporters
        assertThat(mvc.post().uri("/api/v1/reports").multipart()
                .header(HttpHeaders.AUTHORIZATION, TestReporters.bearer(citizens))
                .file("photo", TestPhotos.jpeg())
                .param("category", "TRASH").param("latitude", "-25.03").param("longitude", "-47.00")
                .exchange()).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void routesNotDeclaredPublicRequireLogin() {
        assertThat(mvc.delete().uri("/api/v1/issues/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void bootstrapCreatesTheFirstOperatorOnlyOnce() {
        String email = "primeiro@teste.sp.gov.br";
        SecurityProperties withOperator = new SecurityProperties(properties.jwtSecret(), properties.tokenTtl(),
                properties.issuer(), new SecurityProperties.BootstrapOperator(email, "Primeiro"),
                properties.bootstrapCeo());
        OperatorBootstrapRunner runner = new OperatorBootstrapRunner(withOperator, users, auth);

        runner.run(null);
        Long id = users.findByEmail(email).orElseThrow().getId();
        runner.run(null);

        AppUser admin = users.findByEmail(email).orElseThrow();
        assertThat(admin.getId()).isEqualTo(id);
        assertThat(admin.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(TestLogin.login(mvc, mailbox, email)).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("ADMIN");

        new OperatorBootstrapRunner(new SecurityProperties(properties.jwtSecret(), properties.tokenTtl(),
                properties.issuer(), new SecurityProperties.BootstrapOperator("", "Ninguém"),
                new SecurityProperties.BootstrapOperator("", "Ninguém")), users, auth)
                .run(null);
        assertThat(users.existsByEmail("")).isFalse();
    }

    private MvcTestResult changeStatus(Long id, String authorization) {
        var request = mvc.patch().uri("/api/v1/issues/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"CONFIRMED\"}");
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private Long newIssue(double lat) {
        return reportService.create(TestReporters.ID, "POTHOLE", lat, -47.00, TestPhotos.jpeg()).issueId();
    }

    // assinado como o operador de teste: só o que o teste muda (chave, emissor, papel, datas) pode recusá-lo
    private String token(JwtEncoder encoder, String issuer, List<String> roles, Instant issuedAt, Instant expiresAt) {
        TestOperators.bearer(auth, users);
        String subject = String.valueOf(users.findByEmail(TestOperators.EMAIL).orElseThrow().getId());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim(SecurityConfig.ROLES_CLAIM, roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
