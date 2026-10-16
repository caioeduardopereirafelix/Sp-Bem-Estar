package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

@ConfigurationProperties("app.neighborhoods")
public record NeighborhoodImportProperties(
        Path importFile,
        String city,
        @DefaultValue("name") String nameProperty,
        @DefaultValue("population") String populationProperty,
        @DefaultValue("4326") int sourceSrid
) {
}
