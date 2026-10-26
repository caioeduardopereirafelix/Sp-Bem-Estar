package com.br.spbemestar.controller;

import com.br.spbemestar.TestOperators;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.config.SecurityConfig;
import com.br.spbemestar.config.SecurityProperties;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OperatorManagementTests {

    static final String PASSWORD = "senha-inicial-forte-1";

    @Autowired MockMvcTester mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired JwtEncoder jwtEncoder;
    @Autowired SecurityProperties properties;
    @Autowired JsonMapper json;

    @Test
    void adminRegistersAnOperatorWhoCanLogIn() {
        String email = unique("nova");

        MvcTestResult created = create(admin(), email, "Nova Operadora", PASSWORD, "OPERATOR");

        assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("OPERATOR");
        assertThat(created).bodyText().doesNotContain("password").doesNotContain("{bcrypt}");
        assertThat(login(email, PASSWORD)).hasStatusOk().bodyJson().extractingPath("$.role").isEqualTo("OPERATOR");
        assertThat(mvc.get().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, admin()).exchange())
                .hasStatusOk().bodyJson().extractingPath("$[*].email").asArray().contains(email);
    }

    @Test
    void rejectsDuplicateEmailAndWeakPassword() {
        String email = unique("dupla");
        create(admin(), email, "Dupla", PASSWORD, "OPERATOR");

        assertThat(create(admin(), email.toUpperCase(), "Outra", PASSWORD, "OPERATOR")).hasStatus(HttpStatus.CONFLICT);
        assertThat(create(admin(), unique("curta"), "Curta", "123", "OPERATOR")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.title").isEqualTo("Senha inválida");
        // o bcrypt ignora o que passa de 72 bytes: a senha "longa" valeria só pelo começo
        assertThat(create(admin(), unique("longa"), "Longa", "x".repeat(73), "OPERATOR"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(create(admin(), "não-é-email", "Errado", PASSWORD, "OPERATOR")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void onlyTheAdminManagesTheTeam() {
        String operator = TestOperators.bearer(auth, users);

        assertThat(mvc.get().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, operator).exchange())
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(create(operator, unique("x"), "X", PASSWORD, "ADMIN")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/api/v1/operators").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        // o administrador também é operador: muda status e modera
        assertThat(mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.AUTHORIZATION, admin()).exchange())
                .hasStatusOk();
    }

    @Test
    void deactivationCutsAccessImmediately() {
        String email = unique("sai");
        Long id = createdId(create(admin(), email, "Sai da equipe", PASSWORD, "OPERATOR"));
        String token = bearer(users.findById(id).orElseThrow(), Instant.now().minusSeconds(60));
        assertThat(queue(token)).hasStatusOk();

        assertThat(setActive(id, false)).hasStatusOk().bodyJson().extractingPath("$.active").isEqualTo(false);

        // o token ainda está dentro da validade, mas não vale mais
        assertThat(queue(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(setActive(id, true)).hasStatusOk();
        assertThat(queue(token)).as("o token de antes da desativação continua recusado").hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, PASSWORD)).hasStatusOk();
    }

    @Test
    void adminCannotDeactivateThemselves() {
        Long adminId = users.findByEmail("admin@teste.sp.gov.br").orElseGet(this::newAdmin).getId();

        assertThat(mvc.patch().uri("/api/v1/operators/{id}", adminId).header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}").exchange())
                .hasStatus(HttpStatus.CONFLICT);
        assertThat(setActive(999_999L, false)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void changingThePasswordEndsOtherSessions() {
        String email = unique("senha");
        Long id = createdId(create(admin(), email, "Troca senha", PASSWORD, "OPERATOR"));
        String old = bearer(users.findById(id).orElseThrow(), Instant.now().minusSeconds(60));
        String newPassword = "senha-nova-bem-forte-2";

        assertThat(changePassword(old, "senha-errada-123", newPassword)).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(old, PASSWORD, PASSWORD)).hasStatus(HttpStatus.BAD_REQUEST);
        MvcTestResult changed = changePassword(old, PASSWORD, newPassword);
        assertThat(changed).hasStatusOk();

        String fresh = "Bearer " + json.readTree(changed.getResponse().getContentAsByteArray()).path("accessToken").asString();
        assertThat(queue(fresh)).hasStatusOk();
        assertThat(queue(old)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(login(email, newPassword)).hasStatusOk();
        assertThat(changePassword(null, PASSWORD, newPassword)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult create(String authorization, String email, String name, String password, String role) {
        return mvc.post().uri("/api/v1/operators").header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "name": "%s", "password": "%s", "role": "%s"}
                        """.formatted(email, name, password, role))
                .exchange();
    }

    private MvcTestResult setActive(Long id, boolean active) {
        return mvc.patch().uri("/api/v1/operators/{id}", id).header(HttpHeaders.AUTHORIZATION, admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\": " + active + "}").exchange();
    }

    private MvcTestResult changePassword(String authorization, String current, String newPassword) {
        var request = mvc.put().uri("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\": \"%s\", \"newPassword\": \"%s\"}".formatted(current, newPassword));
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private MvcTestResult queue(String authorization) {
        return mvc.get().uri("/api/v1/issues/queue").header(HttpHeaders.AUTHORIZATION, authorization).exchange();
    }

    private MvcTestResult login(String email, String password) {
        return mvc.post().uri("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)).exchange();
    }

    private Long createdId(MvcTestResult result) {
        return json.readTree(result.getResponse().getContentAsByteArray()).path("id").asLong();
    }

    private String admin() {
        AppUser admin = users.findByEmail("admin@teste.sp.gov.br").orElseGet(this::newAdmin);
        return "Bearer " + auth.issueToken(admin).accessToken();
    }

    private AppUser newAdmin() {
        return auth.createUser("admin@teste.sp.gov.br", "Administradora", PASSWORD, UserRole.ADMIN);
    }

    /** Token emitido antes da mudança: com o mesmo segundo do iat, a revogação não teria como separá-los. */
    private String bearer(AppUser user, Instant issuedAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt).expiresAt(issuedAt.plus(Duration.ofHours(8)))
                .claim(SecurityConfig.ROLES_CLAIM, user.getRole().grantedRoles())
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@teste.sp.gov.br";
    }
}
