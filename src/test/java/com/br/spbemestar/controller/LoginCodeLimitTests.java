package com.br.spbemestar.controller;

import com.br.spbemestar.TestLogin;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Os limites de verdade, que a configuração dos outros testes afrouxa. */
@SpringBootTest(properties = {"app.login.resend-interval=60s", "app.login.max-codes-per-hour=5"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoginCodeLimitTests {

    @Autowired MockMvcTester mvc;

    @Test
    void aNewCodeForTheSameEmailWaitsAMinute() {
        String email = TestReporters.email();
        assertThat(TestLogin.requestCode(mvc, email)).hasStatus(HttpStatus.ACCEPTED)
                .bodyJson().extractingPath("$.resendAfterSeconds").isEqualTo(60);

        MvcTestResult again = TestLogin.requestCode(mvc, email);

        assertThat(again).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
                .bodyJson().extractingPath("$.title").isEqualTo("Espere para pedir outro código");
        assertThat(Long.parseLong(again.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isBetween(1L, 61L);
        // outro e-mail não espera
        assertThat(TestLogin.requestCode(mvc, TestReporters.email())).hasStatus(HttpStatus.ACCEPTED);
    }
}
