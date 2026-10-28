package com.br.spbemestar.controller;

import com.br.spbemestar.TestLogin;
import com.br.spbemestar.TestMailbox;
import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.ReporterJwt;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.repository.CitizenAccountRepository;
import com.br.spbemestar.service.AuthService;
import com.br.spbemestar.service.ReportService;
import com.br.spbemestar.service.ReporterTokenService;
import com.br.spbemestar.service.mail.EmailDeliveryException;
import com.br.spbemestar.service.mail.EmailSender;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginCodeTests {

    @Autowired MockMvcTester mvc;
    @Autowired TestMailbox mailbox;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired CitizenAccountRepository citizens;
    @Autowired ReporterTokenService reporterTokens;
    @Autowired ReporterJwt reporterJwt;
    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void theSameLoginTellsStaffFromCitizens() throws Exception {
        TestOperators.bearer(auth, users);
        String citizenEmail = TestReporters.email();

        MvcTestResult staff = TestLogin.login(mvc, mailbox, TestOperators.EMAIL);
        MvcTestResult citizen = TestLogin.login(mvc, mailbox, citizenEmail);

        assertThat(staff).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("OPERATOR");
        assertThat(staff).bodyJson().extractingPath("$.refreshToken").isNull();
        assertThat(citizen).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("CITIZEN");
        assertThat(citizen).bodyJson().extractingPath("$.email").isEqualTo(citizenEmail);
        assertThat(citizen).bodyJson().extractingPath("$.refreshToken").isNotNull();

        // o token da equipe abre a fila; o do cidadão envia relatos e não abre a fila
        String staffToken = "Bearer " + JsonPath.read(staff.getResponse().getContentAsString(), "$.accessToken");
        String citizenToken = "Bearer " + JsonPath.read(citizen.getResponse().getContentAsString(), "$.accessToken");
        assertThat(mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.AUTHORIZATION, staffToken).exchange())
                .hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.AUTHORIZATION, citizenToken).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(postReport(citizenToken, -27.40)).hasStatus(HttpStatus.CREATED);
        // a equipe não vira conta de cidadão
        assertThat(citizens.findByEmail(TestOperators.EMAIL)).isEmpty();
    }

    @Test
    void theCodeRequestDoesNotRevealWhoIsStaff() {
        TestOperators.bearer(auth, users);

        MvcTestResult staff = TestLogin.requestCode(mvc, TestOperators.EMAIL);
        MvcTestResult citizen = TestLogin.requestCode(mvc, TestReporters.email());

        assertThat(staff).hasStatus(HttpStatus.ACCEPTED);
        assertThat(citizen).hasStatus(HttpStatus.ACCEPTED);
        assertThat(staff.getResponse().getContentAsByteArray().length)
                .isEqualTo(citizen.getResponse().getContentAsByteArray().length);
    }

    @Test
    void theCitizenKeepsTheSameIdentityOnEveryDevice() throws Exception {
        String email = TestReporters.email();

        String first = JsonPath.read(TestLogin.login(mvc, mailbox, email).getResponse().getContentAsString(), "$.accessToken");
        String second = JsonPath.read(TestLogin.login(mvc, mailbox, email.toUpperCase()).getResponse().getContentAsString(),
                "$.accessToken");

        // os relatos de um aparelho aparecem em "Meus relatos" no outro
        assertThat(subject(second)).isEqualTo(subject(first));
    }

    @Test
    void theFirstLoginKeepsTheReportsMadeBefore() throws Exception {
        String anonymous = reporterTokens.issueFor(UUID.randomUUID()).accessToken();
        UUID before = subject(anonymous);
        reportService.create(before, "POTHOLE", -27.41, -48.00, TestPhotos.jpeg());
        String email = TestReporters.email();
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);

        MvcTestResult login = mvc.post().uri("/api/v1/auth/verify").contentType("application/json")
                .content("{\"email\": \"%s\", \"code\": \"%s\", \"reporterToken\": \"%s\"}"
                        .formatted(email, mailbox.code(email), anonymous)).exchange();

        assertThat(login).hasStatusOk();
        assertThat(subject(JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken"))).isEqualTo(before);
    }

    @Test
    void reportingRequiresAConfirmedEmail() {
        String anonymous = "Bearer " + reporterTokens.issueFor(UUID.randomUUID()).accessToken();

        assertThat(postReport(anonymous, -27.42)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.loginRequired").isEqualTo(true);
    }

    @Test
    void aWrongCodeCountsAndTheFifthKillsIt() {
        String email = TestReporters.email();
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);
        String code = mailbox.code(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            assertThat(TestLogin.verify(mvc, email, wrong)).hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.title").isEqualTo("Código inválido");
        }
        // nem o certo vale depois de 5 erros: quem chutou precisa de um código novo, que vai para o e-mail
        assertThat(TestLogin.verify(mvc, email, code)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aCodeWorksOnceAndANewOneReplacesTheOld() {
        String email = TestReporters.email();
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);
        String old = mailbox.code(email);
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);
        String current = mailbox.code(email);

        if (!old.equals(current)) {
            assertThat(TestLogin.verify(mvc, email, old)).hasStatus(HttpStatus.UNAUTHORIZED);
        }
        assertThat(TestLogin.verify(mvc, email, current)).hasStatusOk();
        assertThat(TestLogin.verify(mvc, email, current)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void onlyTheHashOfTheCodeIsStored() {
        String email = TestReporters.email();
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED);
        String code = mailbox.code(email);

        String stored = jdbc.queryForObject("SELECT code_hash FROM login_code WHERE email = ? ORDER BY id DESC LIMIT 1",
                String.class, email);
        assertThat(stored).hasSize(64).doesNotContain(code);
        assertThat(mailbox.last(email).orElseThrow().subject()).contains(code);
    }

    @Test
    void aBlockedCitizenCannotComeBackWithTheSameEmail() throws Exception {
        String email = TestReporters.email();
        String token = "Bearer " + JsonPath.read(TestLogin.login(mvc, mailbox, email).getResponse().getContentAsString(),
                "$.accessToken");
        MvcTestResult report = postReport(token, -27.43);
        Integer reportId = JsonPath.read(report.getResponse().getContentAsString(), "$.reportId");
        TestOperators.bearer(auth, users);
        reporterTokens.blockAuthorOf(reportId.longValue(), users.findByEmail(TestOperators.EMAIL).orElseThrow().getId());

        assertThat(TestLogin.login(mvc, mailbox, email)).hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson().extractingPath("$.title").isEqualTo("Autor bloqueado");
    }

    @Test
    void anEmailThatDidNotGoOutCanBeRequestedAgain() {
        String email = TestReporters.email();
        EmailSender failing = message -> {
            throw new EmailDeliveryException("Brevo fora do ar");
        };
        mailbox.failWith(failing);
        try {
            assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .bodyJson().extractingPath("$.title").isEqualTo("E-mail não enviado");
        } finally {
            mailbox.failWith(null);
        }
        // o código que não saiu não ficou valendo nem contou no limite
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_code WHERE email = ?", Integer.class, email)).isZero();
        assertThat(TestLogin.login(mvc, mailbox, email)).hasStatusOk();
    }

    private MvcTestResult postReport(String authorization, double latitude) {
        return mvc.post().uri("/api/v1/reports").multipart()
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .file("photo", TestPhotos.jpeg())
                .param("category", "TRASH").param("latitude", String.valueOf(latitude)).param("longitude", "-48.00")
                .exchange();
    }

    private UUID subject(String accessToken) {
        return UUID.fromString(reporterJwt.decoder().decode(accessToken).getSubject());
    }
}
