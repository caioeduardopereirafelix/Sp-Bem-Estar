package com.br.spbemestar.service;

import com.br.spbemestar.config.PhotoPrecheckProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Chama o {@code POST /api/classify} do worker. Uma falha (worker fora do ar, demora, resposta estranha) nunca
 * impede o relato: a conferência é um filtro a mais, e a classificação pela fila continua valendo.
 */
@Slf4j
public class HttpPhotoPrecheck implements PhotoPrecheck {

    private final PhotoPrecheckProperties properties;
    private final RestClient client;
    private final MeterRegistry meters;

    public HttpPhotoPrecheck(PhotoPrecheckProperties properties, RestClient client, MeterRegistry meters) {
        this.properties = properties;
        this.client = client;
        this.meters = meters;
    }

    @Override
    public Optional<Prediction> classify(byte[] photo, String contentType) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            Response response = client.post()
                    .uri(properties.url().replaceAll("/+$", "") + "/api/classify")
                    .contentType(MediaType.parseMediaType(contentType))
                    .headers(headers -> {
                        if (properties.username() != null && properties.password() != null) {
                            headers.setBasicAuth(properties.username(), properties.password());
                        }
                    })
                    .body(photo)
                    .retrieve()
                    .body(Response.class);
            if (response == null || response.label() == null) {
                throw new RestClientException("resposta sem categoria");
            }
            return Optional.of(new Prediction(response.label(), response.confidence()));
        } catch (RestClientException | IllegalArgumentException e) {
            meters.counter("spbemestar.reports.precheck", "outcome", "unavailable").increment();
            log.warn("Conferência da foto indisponível, o relato segue sem ela: {}", e.getMessage());
            return Optional.empty();
        }
    }

    record Response(String label, double confidence, String modelVersion) {
    }
}
