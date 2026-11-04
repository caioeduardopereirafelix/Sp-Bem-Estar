package com.br.spbemestar.repository;

import com.br.spbemestar.model.IssueStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class Open311Repository {

    public record Row(long id, String categoryCode, String categoryName, String status, int reporterCount,
                      Instant requestedAt, Instant updatedAt, String resolutionNote, String resolutionPhotoId,
                      String reportPhotoId, String neighborhood, double latitude, double longitude) {
    }

    private static final RowMapper<Row> ROW_MAPPER = (rs, rowNum) -> new Row(
            rs.getLong("id"),
            rs.getString("category_code"),
            rs.getString("category_name"),
            rs.getString("status"),
            rs.getInt("reporter_count"),
            rs.getObject("first_reported_at", OffsetDateTime.class).toInstant(),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
            rs.getString("resolution_note"),
            rs.getString("resolution_photo_id"),
            rs.getString("report_photo_id"),
            rs.getString("neighborhood_name"),
            rs.getDouble("latitude"),
            rs.getDouble("longitude"));

    private static final String SELECT = """
            SELECT i.id, i.category_code, c.name AS category_name, i.status, i.reporter_count,
                   i.first_reported_at, i.resolution_note, i.resolution_photo_id, n.name AS neighborhood_name,
                   ST_Y(i.location::geometry) AS latitude, ST_X(i.location::geometry) AS longitude,
                   GREATEST(i.last_reported_at, i.resolved_at,
                            (SELECT max(h.changed_at) FROM issue_status_history h WHERE h.issue_id = i.id))
                       AS updated_at,
                   (SELECT r.photo_id FROM report r
                    WHERE r.issue_id = i.id
                      AND (r.moderation_status = 'APPROVED'
                           OR (r.moderation_status IS NULL
                               AND r.classification_status NOT IN ('MISMATCH', 'LOW_CONFIDENCE')))
                    ORDER BY r.created_at, r.id LIMIT 1) AS report_photo_id
            FROM issue i
            JOIN category c ON c.code = i.category_code
            LEFT JOIN neighborhood n ON n.id = i.neighborhood_id
            WHERE i.status <> 'DISCARDED'
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<Row> byIds(Collection<Long> ids) {
        return jdbc.query(SELECT + " AND i.id IN (:ids) ORDER BY i.first_reported_at DESC, i.id DESC",
                new MapSqlParameterSource("ids", ids), ROW_MAPPER);
    }

    public List<Row> search(String category, Collection<IssueStatus> statuses, Instant from, Instant to, int limit) {
        StringBuilder sql = new StringBuilder(SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC))
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC))
                .addValue("limit", limit);
        sql.append(" AND i.first_reported_at >= :from AND i.first_reported_at <= :to");
        if (category != null) {
            sql.append(" AND i.category_code = :category");
            params.addValue("category", category);
        }
        if (statuses != null) {
            sql.append(" AND i.status IN (:statuses)");
            params.addValue("statuses", statuses.stream().map(IssueStatus::name).toList());
        }
        sql.append(" ORDER BY i.first_reported_at DESC, i.id DESC LIMIT :limit");
        return jdbc.query(sql.toString(), params, ROW_MAPPER);
    }
}
