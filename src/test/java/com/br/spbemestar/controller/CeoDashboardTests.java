package com.br.spbemestar.controller;

import com.br.spbemestar.TestLogin;
import com.br.spbemestar.TestMailbox;
import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.OperatorBootstrapRunner;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.config.TraceIdResponseFilter;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReportService;
import com.br.testsupport.FailingController;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FailingController.class})
class CeoDashboardTests {

    static final String CEO = "ceo@teste.com.br";
    static final String ADMIN = "admin-do-painel@teste.sp.gov.br";

    @Autowired MockMvcTester mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired ReportService reportService;
    @Autowired SecurityProperties properties;
    @Autowired TestMailbox mailbox;

    @Test
    void onlyTheCeoSeesTheDashboard() {
        assertThat(dashboard(null)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(dashboard(TestOperators.bearer(auth, users))).hasStatus(HttpStatus.FORBIDDEN);
        // nem o administrador da prefeitura: o painel é do dono do produto
        assertThat(dashboard(bearer(ADMIN, UserRole.ADMIN))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(dashboard(bearer(CEO, UserRole.CEO))).hasStatusOk()
                .headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Test
    void thePageIsPublicButEmptyWithoutTheCeoAccount() {
        assertThat(mvc.get().uri("/ceo").exchange()).hasStatus(HttpStatus.FOUND)
                .hasHeader(HttpHeaders.LOCATION, "/ceo.html");
        assertThat(mvc.get().uri("/ceo.html").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/ceo.js").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/ceo.css").exchange()).hasStatusOk();
    }

    @Test
    void theCeoDoesNotTouchIssuesOrTheTeam() {
        String ceo = bearer(CEO, UserRole.CEO);

        assertThat(mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.AUTHORIZATION, ceo).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/moderation/reports").header(HttpHeaders.AUTHORIZATION, ceo).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, ceo).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void theCeoAccountStaysOutOfTheCityTeam() throws Exception {
        Long ceoId = users.findByEmail(CEO).map(AppUser::getId)
                .orElseGet(() -> auth.createUser(CEO, "CEO de teste", UserRole.CEO).getId());
        String admin = bearer(ADMIN, UserRole.ADMIN);

        MvcTestResult team = mvc.get().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, admin).exchange();
        assertThat(team).hasStatusOk();
        List<String> emails = JsonPath.read(team.getResponse().getContentAsString(), "$[*].email");
        assertThat(emails).contains(ADMIN).doesNotContain(CEO);

        assertThat(mvc.patch().uri("/api/v1/operators/{id}", ceoId).header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}").exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(users.findById(ceoId).orElseThrow().isActive()).isTrue();

        assertThat(mvc.post().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "outro-ceo@teste.com.br", "name": "Outro", "role": "CEO"}
                        """).exchange())
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(users.existsByEmail("outro-ceo@teste.com.br")).isFalse();
    }

    @Test
    void theCeoLogsInWithTheEmailCode() throws Exception {
        String email = "ceo-codigo@teste.com.br";
        auth.createUser(email, "CEO", UserRole.CEO);

        MvcTestResult login = TestLogin.login(mvc, mailbox, email);

        assertThat(login).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("CEO");
        assertThat(login).bodyJson().extractingPath("$.refreshToken").isNull();
        String token = "Bearer " + JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        assertThat(dashboard(token)).hasStatusOk();
    }

    @Test
    void bootstrapCreatesTheCeoAccountOnce() {
        String email = "ceo-inicial@teste.com.br";
        SecurityProperties withCeo = new SecurityProperties(properties.jwtSecret(), properties.tokenTtl(),
                properties.issuer(), properties.bootstrapOperator(),
                new SecurityProperties.BootstrapOperator(email, "Dona do produto"));

        new OperatorBootstrapRunner(withCeo, users, auth).run(null);
        Long id = users.findByEmail(email).orElseThrow().getId();
        new OperatorBootstrapRunner(withCeo, users, auth).run(null);

        AppUser ceo = users.findByEmail(email).orElseThrow();
        assertThat(ceo.getRole()).isEqualTo(UserRole.CEO);
        assertThat(ceo.getId()).isEqualTo(id);
    }

    @Test
    void showsProductNumbersAndSystemHealth() {
        reportService.create(TestReporters.ID, "POTHOLE", -21.40, -45.10, TestPhotos.jpeg());

        MvcTestResult result = dashboard(bearer(CEO, UserRole.CEO));

        assertThat(result).hasStatusOk();
        var body = assertThat(result).bodyJson();
        body.extractingPath("$.product.reportsToday").asNumber().satisfies(n -> assertThat(n.longValue()).isPositive());
        body.extractingPath("$.product.reportsTotal").asNumber().satisfies(n -> assertThat(n.longValue()).isPositive());
        body.extractingPath("$.product.reportsByDay").asArray().hasSize(14);
        body.extractingPath("$.product.openByStatus").asMap().containsKeys("OPEN", "CONFIRMED", "IN_PROGRESS");
        body.extractingPath("$.product.photoChecks30d").asMap().containsKeys("AGREED", "MISMATCH", "PENDING");
        body.extractingPath("$.product.topCategories[*].code").asArray().contains("POTHOLE");
        body.extractingPath("$.health.status").isEqualTo("UP");
        body.extractingPath("$.health.components[*].name").asArray().contains("db", "mongo", "rabbit");
        // nos testes não há worker configurado: o painel diz isso em vez de esperar por ele
        body.extractingPath("$.health.worker.status").isEqualTo("NOT_CONFIGURED");
        body.extractingPath("$.health.runtime.uptimeSeconds").asNumber()
                .satisfies(n -> assertThat(n.longValue()).isNotNegative());
        body.extractingPath("$.activity.httpRequests").asNumber()
                .satisfies(n -> assertThat(n.longValue()).isNotNegative());
    }

    @Test
    void listsUnexpectedErrorsWithTheirTraceId() {
        MvcTestResult failure = mvc.get().uri("/api/v1/categories/falha-de-teste?email=alguem@exemplo.com").exchange();
        assertThat(failure).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        String traceId = failure.getResponse().getHeader(TraceIdResponseFilter.HEADER);

        MvcTestResult result = dashboard(bearer(CEO, UserRole.CEO));

        assertThat(result).bodyJson().extractingPath("$.recentErrors[0].path").isEqualTo("/api/v1/categories/falha-de-teste");
        assertThat(result).bodyJson().extractingPath("$.recentErrors[0].method").isEqualTo("GET");
        assertThat(result).bodyJson().extractingPath("$.recentErrors[0].error").isEqualTo("IllegalStateException");
        assertThat(result).bodyJson().extractingPath("$.recentErrors[0].traceId").isEqualTo(traceId);
        // a query string fica de fora: pode trazer dados de quem fez a requisição
        assertThat(result).bodyText().doesNotContain("alguem@exemplo.com");
    }

    private MvcTestResult dashboard(String authorization) {
        var request = mvc.get().uri("/api/v1/ceo/dashboard");
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private String bearer(String email, UserRole role) {
        AppUser user = users.findByEmail(email).orElseGet(() ->
                auth.createUser(email, "Usuário do painel", role));
        return "Bearer " + auth.issueToken(user).accessToken();
    }
}
