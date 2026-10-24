package com.br.spbemestar.repository;

import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    /*
     * prioridade = peso da categoria × ln(1 + pessoas) × (1 + dias em aberto / 30)
     *
     * "Pessoas" são os autores distintos dos relatos não rejeitados: reportar o mesmo problema várias vezes
     * não sobe a prioridade. Relatos sem reporter_hash (anteriores à identidade anônima) contam um cada.
     * O texto é montado em partes para a versão de um chamado só filtrar também a contagem.
     */
    String COUNT_REPORTERS = """
            WITH reporters AS (
                SELECT r.issue_id, count(DISTINCT coalesce(r.reporter_hash, 'relato-' || r.id)) AS total
                FROM report r
                WHERE r.moderation_status IS DISTINCT FROM 'REJECTED'
            """;
    String UPDATE_PRIORITY = """
                GROUP BY r.issue_id
            )
            UPDATE issue i
            SET reporter_count = rp.total,
                priority_score = round((c.severity_weight * ln(1 + rp.total)
                    * (1 + GREATEST(EXTRACT(EPOCH FROM (CAST(:now AS timestamptz) - i.first_reported_at)), 0) / 2592000)
                )::numeric, 4)
            FROM category c, reporters rp
            WHERE c.code = i.category_code
              AND rp.issue_id = i.id
              AND i.status NOT IN ('RESOLVED', 'DISCARDED')
            """;

    @Query(value = """
            SELECT i.* FROM issue i
            WHERE i.category_code = :category
              AND i.status NOT IN ('RESOLVED', 'DISCARDED')
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

    /** Fila de atendimento: mais urgente primeiro; no empate, o mais antigo. */
    @Query(value = """
            SELECT i FROM Issue i JOIN FETCH i.category LEFT JOIN FETCH i.neighborhood
            WHERE i.status IN :statuses
              AND (:category IS NULL OR i.category.code = :category)
              AND (:neighborhoodId IS NULL OR i.neighborhood.id = :neighborhoodId)
            ORDER BY i.priorityScore DESC, i.firstReportedAt, i.id
            """, countQuery = """
            SELECT count(i) FROM Issue i
            WHERE i.status IN :statuses
              AND (:category IS NULL OR i.category.code = :category)
              AND (:neighborhoodId IS NULL OR i.neighborhood.id = :neighborhoodId)
            """)
    Page<Issue> findQueue(@Param("statuses") Collection<IssueStatus> statuses,
                          @Param("category") String category,
                          @Param("neighborhoodId") Long neighborhoodId,
                          Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query(value = COUNT_REPORTERS + " AND r.issue_id = :id " + UPDATE_PRIORITY + " AND i.id = :id", nativeQuery = true)
    int recalculatePriority(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query(value = COUNT_REPORTERS + UPDATE_PRIORITY, nativeQuery = true)
    int recalculateOpenPriorities(@Param("now") Instant now);

    @Query("SELECT c.name FROM Issue i JOIN i.category c WHERE i.id = :id")
    Optional<String> findCategoryNameById(@Param("id") Long id);
}
