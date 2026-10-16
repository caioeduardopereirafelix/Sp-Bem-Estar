package com.br.spbemestar.controller;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.service.NeighborhoodImportService;
import com.br.spbemestar.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NeighborhoodControllerTests {

    @Autowired MockMvcTester mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired NeighborhoodImportService importService;
    @Autowired ReportService reportService;

    @Test
    void ranksNeighborhoodsByOpenIssuesPerTenThousandResidents() {
        // Alfa: 10 mil habitantes; Beta: 20 mil; Gama: 5 mil e nenhum chamado
        importService.importGeoJson(new ByteArrayInputStream(collection(
                        square("Alfa", -44.00, -20.00, 10000),
                        square("Beta", -43.99, -20.00, 20000),
                        square("Gama", -43.98, -20.00, 5000)).getBytes(StandardCharsets.UTF_8)),
                new NeighborhoodImportService.ImportOptions("Cidade Stats", "name", "population", 4326));

        report("POTHOLE", -19.995, -43.995);
        report("TRASH", -19.995, -43.995);
        Long resolved = report("POTHOLE", -19.992, -43.992);
        jdbc.update("UPDATE issue SET status = 'RESOLVED', resolved_at = now() WHERE id = ?", resolved);
        report("POTHOLE", -19.995, -43.985);
        report("POTHOLE", -19.995, -43.985);

        MvcTestResult result = mvc.get().uri("/api/v1/neighborhoods/stats?city={city}", "Cidade Stats").exchange();
        assertThat(result).hasStatusOk();
        JsonNode body = json.readTree(result.getResponse().getContentAsByteArray());

        assertThat(names(body)).containsExactly("Alfa", "Beta", "Gama");

        JsonNode alfa = body.get(0);
        assertThat(alfa.get("openIssues").asInt()).isEqualTo(2);
        assertThat(alfa.get("resolvedIssues").asInt()).isEqualTo(1);
        assertThat(alfa.get("openIssuesPer10k").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(alfa.get("averagePriority").asDouble()).isPositive();

        JsonNode beta = body.get(1);
        assertThat(beta.get("openIssues").asInt()).isEqualTo(1);
        assertThat(beta.get("openReports").asInt()).isEqualTo(2);
        assertThat(beta.get("openIssuesPer10k").decimalValue()).isEqualByComparingTo("0.50");

        JsonNode gama = body.get(2);
        assertThat(gama.get("openIssues").asInt()).isZero();
        assertThat(gama.get("openIssuesPer10k").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(gama.get("averagePriority").isNull()).isTrue();
    }

    private Long report(String category, double lat, double lon) {
        return reportService.create(category, lat, lon, TestPhotos.jpeg()).issueId();
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(n -> names.add(n.get("name").asString()));
        return names;
    }

    private static String collection(String... features) {
        return "{\"type\": \"FeatureCollection\", \"features\": [" + String.join(",", features) + "]}";
    }

    private static String square(String name, double minLon, double minLat, int population) {
        double size = 0.01;
        String ring = String.format(Locale.ROOT, "[[%f,%f],[%f,%f],[%f,%f],[%f,%f],[%f,%f]]",
                minLon, minLat, minLon + size, minLat, minLon + size, minLat + size, minLon, minLat + size,
                minLon, minLat);
        return "{\"type\": \"Feature\", \"properties\": {\"name\": \"" + name + "\", \"population\": " + population
                + "}, \"geometry\": {\"type\": \"Polygon\", \"coordinates\": [" + ring + "]}}";
    }
}
