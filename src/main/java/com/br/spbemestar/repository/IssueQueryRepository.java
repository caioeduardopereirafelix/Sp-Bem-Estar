package com.br.spbemestar.repository;

import com.br.spbemestar.dto.BoundingBox;
import com.br.spbemestar.dto.IssueSummary;
import com.br.spbemestar.model.IssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class IssueQueryRepository {

    private static final RowMapper<IssueSummary> ROW_MAPPER = (rs, rowNum) -> new IssueSummary(
            rs.getLong("id"),
            rs.getString("category_code"),
            rs.getString("category_name"),
            rs.getString("status"),
            rs.getInt("report_count"),
            rs.getInt("reporter_count"),
            rs.getBigDecimal("priority_score"),
            rs.getObject("first_reported_at", OffsetDateTime.class).toInstant(),
            rs.getObject("last_reported_at", OffsetDateTime.class).toInstant(),
            rs.getString("neighborhood_name"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"));

    private final NamedParameterJdbcTemplate jdbc;

    public List<IssueSummary> search(BoundingBox bbox, String category, IssueStatus status, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.category_code, c.name AS category_name, i.status, i.report_count, i.reporter_count,
                       i.priority_score, i.first_reported_at, i.last_reported_at,
                       n.name AS neighborhood_name,
                       ST_Y(i.location::geometry) AS latitude, ST_X(i.location::geometry) AS longitude
                FROM issue i
                JOIN category c ON c.code = i.category_code
                LEFT JOIN neighborhood n ON n.id = i.neighborhood_id
                WHERE 1 = 1
                """);
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);

        if (bbox != null) {
            sql.append(" AND ST_Intersects(i.location, ST_MakeEnvelope(:minLon, :minLat, :maxLon, :maxLat, 4326)::geography)");
            params.addValue("minLon", bbox.minLon())
                    .addValue("minLat", bbox.minLat())
                    .addValue("maxLon", bbox.maxLon())
                    .addValue("maxLat", bbox.maxLat());
        }
        if (category != null) {
            sql.append(" AND i.category_code = :category");
            params.addValue("category", category);
        }
        if (status != null) {
            sql.append(" AND i.status = :status");
            params.addValue("status", status.name());
        } else {
            sql.append(" AND i.status <> 'RESOLVED'");
        }
        sql.append(" AND i.status <> 'DISCARDED'"); // descartado pela moderação: nunca aparece no mapa
        sql.append(" ORDER BY i.priority_score DESC, i.last_reported_at DESC LIMIT :limit");

        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    /**
     * Os chamados para a planilha da prefeitura, do mais antigo para o mais novo. Sem {@code status}, todos menos
     * os descartados pela moderação; {@code from} e {@code to} filtram pelo primeiro relato (fim exclusivo).
     */
    public void export(IssueStatus status, String category, Instant from, Instant to, RowCallbackHandler rows) {
        StringBuilder sql = new StringBuilder("""
                SELECT i.id, i.category_code, c.name AS category_name, i.status, i.report_count, i.reporter_count,
                       i.priority_score, i.first_reported_at, i.last_reported_at, i.resolved_at,
                       i.resolution_note, i.resolution_photo_id, n.name AS neighborhood_name,
                       ST_Y(i.location::geometry) AS latitude, ST_X(i.location::geometry) AS longitude
                FROM issue i
                JOIN category c ON c.code = i.category_code
                LEFT JOIN neighborhood n ON n.id = i.neighborhood_id
                WHERE i.status <> 'DISCARDED'
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (status != null) {
            sql.append(" AND i.status = :status");
            params.addValue("status", status.name());
        }
        if (category != null) {
            sql.append(" AND i.category_code = :category");
            params.addValue("category", category);
        }
        if (from != null) {
            sql.append(" AND i.first_reported_at >= :from");
            params.addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC));
        }
        if (to != null) {
            sql.append(" AND i.first_reported_at < :to");
            params.addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC));
        }
        sql.append(" ORDER BY i.first_reported_at, i.id");
        jdbc.query(sql.toString(), params, rows);
    }
}
