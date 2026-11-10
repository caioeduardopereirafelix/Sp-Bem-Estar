package com.br.spbemestar.classifier.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("classifier")
public record ClassifierProperties(
        Resource model,
        List<String> labels,
        @DefaultValue("224") int inputSize,
        @DefaultValue("dev") String modelVersion,
        @DefaultValue("5s") Duration confirmTimeout,
        String modelSha256
) {
}
