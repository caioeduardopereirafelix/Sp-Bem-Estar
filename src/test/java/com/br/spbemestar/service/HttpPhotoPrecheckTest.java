package com.br.spbemestar.service;

import com.br.spbemestar.config.PhotoPrecheckProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpPhotoPrecheckTest {

    // a senha é gerada a cada execução: nada de segredo fixo no repositório
    private final String password = UUID.randomUUID().toString();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer worker = MockRestServiceServer.bindTo(builder).build();
    private final byte[] photo = {1, 2, 3};

    @Test
    void asksTheWorkerWithTheApiCredentials() {
        worker.expect(requestTo("http://worker:8081/api/classify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder()
                        .encodeToString(("api:" + password).getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().bytes(photo))
                .andRespond(withSuccess("""
                        {"label": "TRASH", "confidence": 0.91, "modelVersion": "v1"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(precheck("http://worker:8081/").classify(photo, MediaType.IMAGE_JPEG_VALUE))
                .contains(new PhotoPrecheck.Prediction("TRASH", 0.91));
        worker.verify();
    }

    @Test
    void aFailingWorkerNeverBlocksTheReport() {
        worker.expect(requestTo("http://worker:8081/api/classify")).andRespond(withServerError());

        assertThat(precheck("http://worker:8081").classify(photo, MediaType.IMAGE_JPEG_VALUE)).isEmpty();
        assertThat(meters.counter("spbemestar.reports.precheck", "outcome", "unavailable").count()).isEqualTo(1);
    }

    @Test
    void doesNothingWhenTurnedOff() {
        worker.expect(never(), requestTo("http://worker:8081/api/classify"));

        assertThat(precheck("").classify(photo, MediaType.IMAGE_JPEG_VALUE)).isEmpty();
        worker.verify();
    }

    private HttpPhotoPrecheck precheck(String url) {
        return new HttpPhotoPrecheck(new PhotoPrecheckProperties(url, "api", password, Duration.ofSeconds(3), 0.8),
                builder.build(), meters);
    }
}
