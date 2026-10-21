package com.br.spbemestar.repository;

import com.br.spbemestar.dto.NeighborhoodStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class NeighborhoodStatsRepository {

    private static final RowMapper<NeighborhoodStats> ROW_MAPPER = (rs, rowNum) -> new NeighborhoodStats(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("city"),
            rs.getObject("population", Integer.class),
            rs.getInt("open_issues"),
            rs.getInt("resolved_issues"),
            rs.getInt("open_reports"),
            rs.getBigDecimal("open_per_10k"),
            rs.getBigDecimal("average_priority"));

    private final NamedParameterJdbcTemplate jdbc;

    // Ordena por chamados abertos a cada 10 mil habitantes: bairros populosos não dominam o ranking só pelo volume
    public List<NeighborhoodStats> findAll(String city) {
        StringBuilder sql = new StringBuilder("""
                SELECT n.id, n.name, n.city, n.population,
                       count(i.id) FILTER (WHERE i.status NOT IN ('RESOLVED', 'DISCARDED')) AS open_issues,
                       count(i.id) FILTER (WHERE i.status = 'RESOLVED') AS resolved_issues,
                       coalesce(sum(i.report_count) FILTER (WHERE i.status NOT IN ('RESOLVED', 'DISCARDED')), 0) AS open_reports,
                       round(count(i.id) FILTER (WHERE i.status NOT IN ('RESOLVED', 'DISCARDED')) * 10000.0 / nullif(n.population, 0), 2)
                           AS open_per_10k,
                       round(avg(i.priority_score) FILTER (WHERE i.status NOT IN ('RESOLVED', 'DISCARDED')), 4) AS average_priority
                FROM neighborhood n
                LEFT JOIN issue i ON i.neighborhood_id = n.id
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (city != null) {
            sql.append(" WHERE n.city = :city");
            params.addValue("city", city);
        }
        sql.append("""
                 GROUP BY n.id
                 ORDER BY open_per_10k DESC NULLS LAST, open_issues DESC, n.name
                """);
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    // Simplificação de ~10 m: reduz o tamanho da resposta sem alterar o desenho na escala da cidade
    public Map<Long, String> findSimplifiedGeometries(String city) {
        String sql = "SELECT id, ST_AsGeoJSON(ST_Multi(ST_SimplifyPreserveTopology(geom, 0.0001)), 6) AS geometry FROM neighborhood"
                + (city == null ? "" : " WHERE city = :city");
        Map<Long, String> geometries = new HashMap<>();
        jdbc.query(sql, new MapSqlParameterSource("city", city),
                rs -> { geometries.put(rs.getLong("id"), rs.getString("geometry")); });
        return geometries;
    }
}
