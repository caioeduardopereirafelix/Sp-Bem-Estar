package com.br.spbemestar.repository;

import com.br.spbemestar.model.CitizenAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CitizenAccountRepository extends JpaRepository<CitizenAccount, Long> {

    Optional<CitizenAccount> findByEmail(String email);

    boolean existsByReporterId(UUID reporterId);
}
