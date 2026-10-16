package com.br.spbemestar.config;

import com.br.spbemestar.service.NeighborhoodImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;

/**
 * Importa bairros na inicialização quando {@code app.neighborhoods.import-file} é informado:
 * <pre>
 * ./mvnw spring-boot:run "-Dspring-boot.run.arguments=--app.neighborhoods.import-file=data/sp-distritos.geojson '--app.neighborhoods.city=São Paulo'"
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(NeighborhoodImportProperties.class)
@ConditionalOnProperty("app.neighborhoods.import-file")
public class NeighborhoodImportRunner implements ApplicationRunner {

    private final NeighborhoodImportService importService;
    private final NeighborhoodImportProperties properties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (properties.city() == null || properties.city().isBlank()) {
            throw new IllegalStateException("Informe a cidade em app.neighborhoods.city");
        }
        log.info("Importando bairros de {} para {}", properties.importFile(), properties.city());
        try (InputStream content = Files.newInputStream(properties.importFile())) {
            var result = importService.importGeoJson(content, new NeighborhoodImportService.ImportOptions(
                    properties.city(), properties.nameProperty(), properties.populationProperty(),
                    properties.sourceSrid()));
            log.info("{} bairros importados; {} chamados associados ao bairro",
                    result.neighborhoodsImported(), result.issuesAssigned());
        }
    }
}
