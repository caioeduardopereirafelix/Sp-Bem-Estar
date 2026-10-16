package com.br.spbemestar.service;

import com.br.spbemestar.exception.InvalidNeighborhoodFileException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NeighborhoodImportService {

    // Reprojeta para WGS84, corrige geometrias inválidas e garante MultiPolygon
    private static final String UPSERT = """
            INSERT INTO neighborhood (name, city, population, geom)
            VALUES (:name, :city, :population,
                    ST_Multi(ST_CollectionExtract(ST_MakeValid(
                        ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:geometry), :srid), 4326)), 3)))
            ON CONFLICT (city, name) DO UPDATE
                SET population = EXCLUDED.population,
                    geom = EXCLUDED.geom
            """;

    // Bumpa a versão para que uma atualização JPA concorrente não sobrescreva o bairro
    private static final String ASSIGN_ISSUES = """
            UPDATE issue i
            SET neighborhood_id = n.id, version = i.version + 1
            FROM neighborhood n
            WHERE n.city = :city
              AND ST_Contains(n.geom, i.location::geometry)
              AND i.neighborhood_id IS DISTINCT FROM n.id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    @Transactional
    public ImportResult importGeoJson(InputStream content, ImportOptions options) {
        JsonNode root = json.readTree(content);
        if (!"FeatureCollection".equals(text(root.get("type")))) {
            throw new InvalidNeighborhoodFileException("O arquivo precisa ser um GeoJSON do tipo FeatureCollection");
        }

        Set<String> names = new HashSet<>();
        int imported = 0;
        int position = 0;
        for (JsonNode feature : root.path("features")) {
            position++;
            JsonNode properties = feature.path("properties");
            JsonNode geometry = feature.get("geometry");

            String name = text(properties.get(options.nameProperty()));
            if (name == null || name.isBlank()) {
                throw new InvalidNeighborhoodFileException(
                        "Feature #" + position + " sem a propriedade '" + options.nameProperty() + "'");
            }
            name = name.trim();
            if (!names.add(name)) {
                throw new InvalidNeighborhoodFileException("Bairro repetido no arquivo: " + name);
            }
            String geometryType = geometry == null ? null : text(geometry.get("type"));
            if (!"Polygon".equals(geometryType) && !"MultiPolygon".equals(geometryType)) {
                throw new InvalidNeighborhoodFileException(
                        "Bairro '" + name + "' precisa ter geometria Polygon ou MultiPolygon");
            }

            jdbc.update(UPSERT, new MapSqlParameterSource()
                    .addValue("name", name)
                    .addValue("city", options.city())
                    .addValue("population", population(properties.get(options.populationProperty())))
                    .addValue("geometry", json.writeValueAsString(geometry))
                    .addValue("srid", options.sourceSrid()));
            imported++;
        }
        if (imported == 0) {
            throw new InvalidNeighborhoodFileException("O arquivo não contém bairros");
        }

        int issuesAssigned = jdbc.update(ASSIGN_ISSUES, new MapSqlParameterSource("city", options.city()));
        return new ImportResult(imported, issuesAssigned);
    }

    private static Integer population(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.asInt();
        }
        String digits = text(value).replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.valueOf(digits);
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asString();
    }

    public record ImportOptions(String city, String nameProperty, String populationProperty, int sourceSrid) {
    }

    public record ImportResult(int neighborhoodsImported, int issuesAssigned) {
    }
}
