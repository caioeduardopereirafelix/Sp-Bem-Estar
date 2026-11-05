package com.br.spbemestar.service.dashboard;

import com.br.spbemestar.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProductMetrics {

    private static final String PRODUCT = """
            SELECT
                (SELECT count(*) FROM report
                 WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'America/Sao_Paulo')
                                      AT TIME ZONE 'America/Sao_Paulo')) AS reports_today,
                (SELECT count(*) FROM report WHERE created_at > now() - interval '7 days') AS reports_7d,
                (SELECT count(*) FROM report WHERE created_at > now() - interval '30 days') AS reports_30d,
                (SELECT count(*) FROM report) AS reports_total,
                (SELECT count(DISTINCT reporter_hash) FROM report WHERE created_at > now() - interval '30 days')
                    AS reporters_30d,
                (SELECT count(*) FROM issue WHERE status = 'RESOLVED' AND resolved_at > now() - interval '30 days')
                    AS resolved_30d,
                (SELECT count(*) FROM issue WHERE status = 'RESOLVED') AS resolved_total,
                (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM resolved_at - first_reported_at))
                 FROM issue WHERE status = 'RESOLVED' AND resolved_at > now() - interval '30 days') / 3600
                    AS median_resolution_hours,
                (SELECT count(*) FROM report
                 WHERE classification_status IN ('MISMATCH', 'LOW_CONFIDENCE') AND moderation_status IS NULL)
                    AS moderation_pending,
                (SELECT count(*) FROM report
                 WHERE moderation_status = 'REJECTED' AND moderated_at > now() - interval '30 days') AS rejected_30d,
                (SELECT count(*) FROM push_subscription) AS push_subscriptions,
                (SELECT count(*) FROM citizen_account) AS citizen_accounts
            """;

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public DashboardResponse.Product read() {
        Map<String, Object> row = jdbc.queryForMap(PRODUCT);
        List<DashboardResponse.DailyCount> byDay = jdbc.query("""
                SELECT d::date AS day, count(r.id) AS total
                FROM generate_series((now() AT TIME ZONE 'America/Sao_Paulo')::date - 13,
                                     (now() AT TIME ZONE 'America/Sao_Paulo')::date, interval '1 day') AS d
                LEFT JOIN report r ON (r.created_at AT TIME ZONE 'America/Sao_Paulo')::date = d::date
                GROUP BY d ORDER BY d
                """, (rs, i) -> new DashboardResponse.DailyCount(rs.getDate("day").toLocalDate(), rs.getLong("total")));
        Map<String, Long> openByStatus = new LinkedHashMap<>(linkedCounts("OPEN", "CONFIRMED", "IN_PROGRESS"));
        jdbc.query("""
                SELECT status, count(*) AS total FROM issue
                WHERE status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS') GROUP BY status
                """, rs -> {
            openByStatus.put(rs.getString("status"), rs.getLong("total"));
        });
        Map<String, Long> photoChecks = new LinkedHashMap<>(
                linkedCounts("AGREED", "MISMATCH", "LOW_CONFIDENCE", "PENDING"));
        jdbc.query("""
                SELECT classification_status, count(*) AS total FROM report
                WHERE created_at > now() - interval '30 days' GROUP BY classification_status
                """, rs -> {
            photoChecks.put(rs.getString("classification_status"), rs.getLong("total"));
        });
        Number median = (Number) row.get("median_resolution_hours");
        return new DashboardResponse.Product(
                number(row, "reports_today"), number(row, "reports_7d"), number(row, "reports_30d"),
                number(row, "reports_total"), byDay, number(row, "reporters_30d"), openByStatus,
                number(row, "resolved_30d"), number(row, "resolved_total"),
                median == null ? null : Math.round(median.doubleValue() * 10) / 10.0,
                ranked("""
                        SELECT c.code, c.name, count(*) AS total FROM issue i JOIN category c ON c.code = i.category_code
                        WHERE i.status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS')
                        GROUP BY c.code, c.name ORDER BY total DESC, c.name LIMIT 5
                        """),
                ranked("""
                        SELECT n.id::text AS code, n.name, count(*) AS total FROM issue i
                        JOIN neighborhood n ON n.id = i.neighborhood_id
                        WHERE i.status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS')
                        GROUP BY n.id, n.name ORDER BY total DESC, n.name LIMIT 5
                        """),
                number(row, "moderation_pending"), number(row, "rejected_30d"), photoChecks,
                number(row, "push_subscriptions"), number(row, "citizen_accounts"));
    }

    private List<DashboardResponse.Ranked> ranked(String sql) {
        return jdbc.query(sql, (rs, i) -> new DashboardResponse.Ranked(rs.getString("code"), rs.getString("name"),
                rs.getLong("total")));
    }

    private static long number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Map<String, Long> linkedCounts(String... keys) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : keys) {
            counts.put(key, 0L);
        }
        return counts;
    }
}
