package com.br.spbemestar.repository;

import com.br.spbemestar.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    // prioridade = peso da categoria × ln(1 + relatos) × (1 + dias em aberto / 30)
    String RECALCULATE_PRIORITY = """
            UPDATE issue i
            SET priority_score = round((c.severity_weight * ln(1 + i.report_count)
                    * (1 + GREATEST(EXTRACT(EPOCH FROM (CAST(:now AS timestamptz) - i.first_reported_at)), 0) / 2592000)
                )::numeric, 4)
            FROM category c
            WHERE c.code = i.category_code
              AND i.status <> 'RESOLVED'
            """;

    @Query(value = """
            SELECT i.* FROM issue i
            WHERE i.category_code = :category
              AND i.status <> 'RESOLVED'
              AND i.last_reported_at > :since
              AND ST_DWithin(i.location, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography, :radius)
            ORDER BY i.location <-> ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)::geography
            LIMIT 1
            """, nativeQuery = true)
    Optional<Issue> findNearestOpen(@Param("category") String category,
                                    @Param("latitude") double latitude,
                                    @Param("longitude") double longitude,
                                    @Param("radius") double radiusMeters,
                                    @Param("since") Instant since);

    @Modifying(flushAutomatically = true)
    @Query(value = RECALCULATE_PRIORITY + " AND i.id = :id", nativeQuery = true)
    int recalculatePriority(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(value = RECALCULATE_PRIORITY, nativeQuery = true)
    int recalculateOpenPriorities(@Param("now") Instant now);
}
