package com.br.spbemestar.repository;

import com.br.spbemestar.model.ClassificationStatus;
import com.br.spbemestar.model.Report;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByIssueIdOrderByCreatedAt(Long issueId);

    /** Relatos que aparecem no chamado: os rejeitados pela moderação ficam de fora. */
    @Query("""
            SELECT r FROM Report r
            WHERE r.issue.id = :issueId
              AND (r.moderationStatus IS NULL OR r.moderationStatus <> com.br.spbemestar.model.ModerationDecision.REJECTED)
            ORDER BY r.createdAt
            """)
    List<Report> findVisibleByIssue(@Param("issueId") Long issueId);

    /** Fila de moderação: marcados pelo classificador e ainda sem decisão, do mais antigo para o mais novo. */
    @Query(value = """
            SELECT r FROM Report r JOIN FETCH r.issue JOIN FETCH r.category
            WHERE r.classificationStatus IN :flags AND r.moderationStatus IS NULL
            ORDER BY r.createdAt, r.id
            """, countQuery = """
            SELECT count(r) FROM Report r
            WHERE r.classificationStatus IN :flags AND r.moderationStatus IS NULL
            """)
    Page<Report> findPendingModeration(@Param("flags") Collection<ClassificationStatus> flags, Pageable pageable);

    @Query("""
            SELECT r.issue.id, count(r) FROM Report r
            WHERE r.issue.id IN :issueIds AND r.classificationStatus IN :flags AND r.moderationStatus IS NULL
            GROUP BY r.issue.id
            """)
    List<Object[]> countPendingModerationByIssue(@Param("issueIds") Collection<Long> issueIds,
                                                 @Param("flags") Collection<ClassificationStatus> flags);

    // Trava o relato: dois operadores decidindo ao mesmo tempo não podem descontar o chamado duas vezes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Report r JOIN FETCH r.issue WHERE r.id = :id")
    Optional<Report> findForModeration(@Param("id") Long id);
}
