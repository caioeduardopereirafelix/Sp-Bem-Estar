package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties("app.classification")
public record ClassificationProperties(
        @DefaultValue("0.6") BigDecimal minConfidence
) {
}
