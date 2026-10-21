package com.br.spbemestar.repository;

import com.br.spbemestar.model.ReporterRefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ReporterRefreshTokenRepository extends JpaRepository<ReporterRefreshToken, UUID> {

    // Trava a linha: duas renovações simultâneas com o mesmo token não podem ambas dar certo
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ReporterRefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE ReporterRefreshToken t SET t.revokedAt = :now WHERE t.reporterId = :reporterId AND t.revokedAt IS NULL")
    int revokeAllOf(@Param("reporterId") UUID reporterId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM ReporterRefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
