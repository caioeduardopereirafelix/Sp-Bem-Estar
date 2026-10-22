package com.br.spbemestar.config;

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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityHardeningTests {

    @Autowired MockMvcTester mvc;

    @Test
    void metricsRequireThePrometheusCredentials() {
        assertThat(metrics(null)).hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"metrics\"")
                .bodyJson().extractingPath("$.title").isEqualTo("Não autenticado");
        assertThat(metrics(basic("prometheus-teste", "senha-errada"))).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(metrics(basic("operador", "senha-do-prometheus-teste"))).hasStatus(HttpStatus.UNAUTHORIZED);

        MvcTestResult ok = metrics(basic("prometheus-teste", "senha-do-prometheus-teste"));
        assertThat(ok).hasStatusOk();
        assertThat(ok).bodyText().contains("jvm_memory_used_bytes");
    }

    @Test
    void operatorTokenDoesNotOpenMetrics() {
        assertThat(metrics("Bearer qualquer-token")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void healthStaysPublic() {
        assertThat(mvc.get().uri("/actuator/health").exchange()).hasStatusOk();
    }

    @Test
    void mapIsServedWithContentSecurityPolicy() {
        MvcTestResult page = mvc.get().uri("/").exchange();

        assertThat(page).hasStatusOk();
        String csp = page.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).isEqualTo(SecurityConfig.CONTENT_SECURITY_POLICY)
                .contains("script-src 'self' https://cdnjs.cloudflare.com", "frame-ancestors 'none'")
                .doesNotContain("unsafe-inline", "unsafe-eval");
        assertThat(page).hasHeader("X-Content-Type-Options", "nosniff");
        // a localização do usuário só pode ser pedida pela própria página, e câmera e microfone por ninguém
        assertThat(page).hasHeader("Permissions-Policy", "geolocation=(self), camera=(), microphone=(), payment=(), usb=()");
    }

    @Test
    void onlySwaggerAcceptsInlineStyles() {
        MvcTestResult swagger = mvc.get().uri("/swagger-ui/index.html").exchange();

        assertThat(swagger).hasStatusOk()
                .hasHeader("Content-Security-Policy", SecurityConfig.SWAGGER_CONTENT_SECURITY_POLICY);
        assertThat(SecurityConfig.SWAGGER_CONTENT_SECURITY_POLICY)
                .contains("style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com")
                .contains("script-src 'self' https://cdnjs.cloudflare.com");
    }

    @Test
    void mapLoadsLeafletWithSubresourceIntegrity() throws Exception {
        // "/" só encaminha para o index.html; o MockMvc não segue o forward
        String html = mvc.get().uri("/index.html").exchange().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).containsPattern("leaflet\\.min\\.js\"\\s+integrity=\"sha512-")
                .containsPattern("leaflet\\.min\\.css\"\\s+integrity=\"sha512-")
                .doesNotContain("style=\"");
    }

    private MvcTestResult metrics(String authorization) {
        var request = mvc.get().uri(SecurityConfig.METRICS_PATH);
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
