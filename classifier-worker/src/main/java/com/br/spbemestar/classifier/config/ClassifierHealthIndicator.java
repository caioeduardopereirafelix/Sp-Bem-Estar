package com.br.spbemestar.classifier.config;

import com.br.spbemestar.classifier.service.OnnxPhotoClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * O modelo é carregado na subida: se o worker está de pé, o modelo está em memória. O health check
 * diz qual versão e quantas categorias, para conferir num deploy se o modelo certo subiu.
 */
@Component("classifier")
@RequiredArgsConstructor
public class ClassifierHealthIndicator implements HealthIndicator {

    private final OnnxPhotoClassifier classifier;
    private final ClassifierProperties properties;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("modelVersion", properties.modelVersion())
                .withDetail("labels", classifier.labels().size())
                .withDetail("inputSize", classifier.inputSize())
                .build();
    }
}
