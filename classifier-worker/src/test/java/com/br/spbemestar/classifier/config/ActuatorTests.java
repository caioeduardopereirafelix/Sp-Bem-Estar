package com.br.spbemestar.classifier.config;

import com.br.spbemestar.classifier.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "classifier.model=classpath:models/color-test.onnx",
        "classifier.model-version=color-test",
        "app.metrics.username=prometheus-teste",
        "app.metrics.password=senha-do-prometheus-teste"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActuatorTests {

    @Autowired MockMvcTester mvc;

    @Test
    void healthIsPublicAndIncludesTheModel() {
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/classifier").exchange()).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(mvc.get().uri("/actuator/health/readiness").exchange()).hasStatusOk();
    }

    @Test
    void metricsRequireThePrometheusCredentials() {
        assertThat(mvc.get().uri("/actuator/prometheus").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basic("prometheus-teste", "errada")).exchange())
                .hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult ok = mvc.get().uri("/actuator/prometheus")
                .header(HttpHeaders.AUTHORIZATION, basic("prometheus-teste", "senha-do-prometheus-teste")).exchange();
        assertThat(ok).hasStatusOk();
        assertThat(ok).bodyText().contains("jvm_memory_used_bytes", "rabbitmq_");
    }

    @Test
    void nothingElseIsExposed() {
        assertThat(mvc.get().uri("/actuator/env").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/qualquer-coisa").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
