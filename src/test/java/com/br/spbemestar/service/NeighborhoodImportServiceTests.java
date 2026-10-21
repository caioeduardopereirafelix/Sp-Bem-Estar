package com.br.spbemestar.service;

import com.br.spbemestar.TestPhotos;
import com.br.spbemestar.TestReporters;
import com.br.spbemestar.TestcontainersConfiguration;
import com.br.spbemestar.exception.InvalidNeighborhoodFileException;
import com.br.spbemestar.repository.NeighborhoodRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NeighborhoodImportServiceTests {

    @Autowired NeighborhoodImportService importService;
    @Autowired NeighborhoodRepository neighborhoods;
    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void importsPolygonsAndMultiPolygonsAsWgs84MultiPolygons() {
        String file = collection(
                square("Centro", -47.90, -15.80, 0.01, "50000", false),
                square("Norte", -47.90, -15.79, 0.01, "12.345", true));

        var result = importGeoJson(file, "Cidade A", 4326);

        assertThat(result.neighborhoodsImported()).isEqualTo(2);
        assertThat(jdbc.queryForList("""
                SELECT name || ':' || population || ':' || ST_GeometryType(geom) || ':' || ST_SRID(geom)
                FROM neighborhood WHERE city = 'Cidade A' ORDER BY name
                """, String.class))
                .containsExactly("Centro:50000:ST_MultiPolygon:4326", "Norte:12345:ST_MultiPolygon:4326");
        assertThat(neighborhoods.findContaining(-15.795, -47.895))
                .hasValueSatisfying(n -> assertThat(n.getName()).isEqualTo("Centro"));
    }

    @Test
    void reimportUpdatesInsteadOfDuplicating() {
        importGeoJson(collection(square("Sul", -47.90, -15.90, 0.01, "100", false)), "Cidade B", 4326);

        importGeoJson(collection(square("Sul", -47.90, -15.90, 0.01, "200", false)), "Cidade B", 4326);

        assertThat(jdbc.queryForList("SELECT population FROM neighborhood WHERE city = 'Cidade B'", Integer.class))
                .containsExactly(200);
    }

    @Test
    void reprojectsFromSirgasUtm23S() {
        // Quadrado [-47.80, -15.60] a [-47.79, -15.59] em EPSG:31983, calculado pelo próprio PostGIS
        String utm = """
                {"type": "Feature", "properties": {"name": "UTM"},
                 "geometry": {"type": "Polygon", "coordinates": [[[199731.94,8273335.39],[199717.38,8274442.63],
                   [200790.55,8274456.72],[200805.06,8273349.48],[199731.94,8273335.39]]]}}
                """;

        importGeoJson(collection(utm), "Cidade UTM", 31983);

        assertThat(neighborhoods.findContaining(-15.595, -47.795))
                .hasValueSatisfying(n -> assertThat(n.getCity()).isEqualTo("Cidade UTM"));
    }

    @Test
    void assignsExistingIssuesToImportedNeighborhood() {
        Long issueId = reportService.create(TestReporters.ID, "POTHOLE", -15.495, -47.695, TestPhotos.jpeg()).issueId();
        assertThat(neighborhoodOf(issueId)).isNull();

        var result = importGeoJson(collection(square("Leste", -47.70, -15.50, 0.01, "10", false)), "Cidade C", 4326);

        assertThat(result.issuesAssigned()).isEqualTo(1);
        assertThat(neighborhoodOf(issueId)).isEqualTo("Leste");
    }

    @Test
    void rejectsInvalidFilesWithoutImportingAnything() {
        String missingName = collection(
                square("Valido", -47.60, -15.40, 0.01, "1", false),
                """
                {"type": "Feature", "properties": {"nome": "Sem campo name"},
                 "geometry": {"type": "Polygon", "coordinates": [[[0,0],[1,0],[1,1],[0,0]]]}}
                """);

        assertThatThrownBy(() -> importGeoJson(missingName, "Cidade D", 4326))
                .isInstanceOf(InvalidNeighborhoodFileException.class)
                .hasMessageContaining("Feature #2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM neighborhood WHERE city = 'Cidade D'", Integer.class))
                .isZero();

        assertThatThrownBy(() -> importGeoJson("{\"type\": \"Feature\"}", "Cidade D", 4326))
                .isInstanceOf(InvalidNeighborhoodFileException.class);
        assertThatThrownBy(() -> importGeoJson(collection(
                square("Dup", -47.60, -15.40, 0.01, "1", false),
                square("Dup", -47.60, -15.40, 0.01, "1", false)), "Cidade D", 4326))
                .hasMessageContaining("repetido");
    }

    @Test
    void usesConfiguredPropertyNames() {
        String file = collection("""
                {"type": "Feature", "properties": {"nm_distrito": "Distrito X", "pop_2022": 777},
                 "geometry": {"type": "Polygon", "coordinates": [[[-47.5,-15.3],[-47.49,-15.3],[-47.49,-15.29],[-47.5,-15.29],[-47.5,-15.3]]]}}
                """);

        importService.importGeoJson(stream(file),
                new NeighborhoodImportService.ImportOptions("Cidade E", "nm_distrito", "pop_2022", 4326));

        assertThat(jdbc.queryForObject("SELECT name || ':' || population FROM neighborhood WHERE city = 'Cidade E'",
                String.class)).isEqualTo("Distrito X:777");
    }

    private NeighborhoodImportService.ImportResult importGeoJson(String content, String city, int srid) {
        return importService.importGeoJson(stream(content),
                new NeighborhoodImportService.ImportOptions(city, "name", "population", srid));
    }

    private String neighborhoodOf(Long issueId) {
        return jdbc.queryForObject("""
                SELECT n.name FROM issue i LEFT JOIN neighborhood n ON n.id = i.neighborhood_id WHERE i.id = ?
                """, String.class, issueId);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String collection(String... features) {
        return "{\"type\": \"FeatureCollection\", \"features\": [" + String.join(",", features) + "]}";
    }

    private static String square(String name, double minLon, double minLat, double size, String population,
                                 boolean multi) {
        String ring = String.format(Locale.ROOT, "[[%f,%f],[%f,%f],[%f,%f],[%f,%f],[%f,%f]]",
                minLon, minLat, minLon + size, minLat, minLon + size, minLat + size, minLon, minLat + size,
                minLon, minLat);
        String geometry = multi
                ? "{\"type\": \"MultiPolygon\", \"coordinates\": [[" + ring + "]]}"
                : "{\"type\": \"Polygon\", \"coordinates\": [" + ring + "]}";
        return "{\"type\": \"Feature\", \"properties\": {\"name\": \"" + name + "\", \"population\": \""
                + population + "\"}, \"geometry\": " + geometry + "}";
    }
}
