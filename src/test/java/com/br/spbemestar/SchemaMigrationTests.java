package com.br.spbemestar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaMigrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void postgisExtensionIsInstalled() {
        String version = jdbc.queryForObject("SELECT postgis_version()", String.class);

        assertThat(version).startsWith("3.");
    }

    @Test
    void categoriesAreSeeded() {
        List<String> codes = jdbc.queryForList("SELECT code FROM category ORDER BY code", String.class);

        assertThat(codes).containsExactly("BUS_STOP", "CLOGGED_DRAIN", "FLOODING", "OVERGROWN", "POTHOLE",
                "SEWAGE", "SIDEWALK", "STREETLIGHT", "TRASH", "TREE");
    }

    @Test
    void transientProblemsGroupInShorterWindows() {
        Integer flooding = jdbc.queryForObject(
                "SELECT dedup_window_days FROM category WHERE code = 'FLOODING'", Integer.class);
        Integer sidewalk = jdbc.queryForObject(
                "SELECT dedup_window_days FROM category WHERE code = 'SIDEWALK'", Integer.class);

        assertThat(flooding).isLessThan(sidewalk);
    }

    @Test
    void spatialColumnsUseExpectedTypes() {
        String issueLocation = jdbc.queryForObject("""
                SELECT type FROM geography_columns
                WHERE f_table_name = 'issue' AND f_geography_column = 'location'
                """, String.class);
        String neighborhoodGeom = jdbc.queryForObject("""
                SELECT type FROM geometry_columns
                WHERE f_table_name = 'neighborhood' AND f_geometry_column = 'geom'
                """, String.class);

        assertThat(issueLocation).isEqualToIgnoringCase("Point");
        assertThat(neighborhoodGeom).isEqualToIgnoringCase("MultiPolygon");
    }

    @Test
    void geographyDistanceIsMeasuredInMeters() {
        // Dois pontos na Av. Paulista separados por ~0.0001° de latitude (~11 m)
        Boolean within15m = jdbc.queryForObject("""
                SELECT ST_DWithin(
                    ST_MakePoint(-46.6544, -23.5614)::geography,
                    ST_MakePoint(-46.6544, -23.5615)::geography,
                    15)
                """, Boolean.class);

        assertThat(within15m).isTrue();
    }

    @Test
    void resolvedIssueRequiresResolvedAt() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO issue (category_code, location, status, first_reported_at, last_reported_at)
                VALUES ('POTHOLE', ST_MakePoint(-46.6544, -23.5614)::geography, 'RESOLVED', now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
