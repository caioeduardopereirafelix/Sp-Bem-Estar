package com.br.spbemestar.repository;

import com.br.spbemestar.model.LoginCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface LoginCodeRepository extends JpaRepository<LoginCode, Long> {

    // Trava a linha: duas tentativas ao mesmo tempo não podem passar do limite nem usar o código duas vezes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LoginCode> findFirstByEmailAndConsumedAtIsNullOrderByCreatedAtDesc(String email);

    Optional<LoginCode> findFirstByEmailOrderByCreatedAtDesc(String email);

    long countByEmailAndCreatedAtAfter(String email, Instant since);

    /** Um código novo invalida os anteriores: só o último e-mail recebido vale. */
    @Modifying
    @Query("UPDATE LoginCode c SET c.consumedAt = :now WHERE c.email = :email AND c.consumedAt IS NULL")
    int invalidatePending(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM LoginCode c WHERE c.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
