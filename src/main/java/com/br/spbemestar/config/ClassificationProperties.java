package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/**
 * @param minConfidence abaixo dessa confiança o relato vai para moderação, mesmo que a categoria bata
 */
@ConfigurationProperties("app.classification")
public record ClassificationProperties(
        @DefaultValue("0.6") BigDecimal minConfidence
) {
}
