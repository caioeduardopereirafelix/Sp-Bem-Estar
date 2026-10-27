package com.br.spbemestar.service;

import com.br.spbemestar.dto.ReporterTokenResponse;
import com.br.spbemestar.exception.CitizenLoginRequiredException;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.CitizenAccount;
import com.br.spbemestar.repository.CitizenAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/** Contas de cidadão: o e-mail confirmado por código e o id que assina os relatos dele. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CitizenAccountService {

    private final CitizenAccountRepository accounts;
    private final ReporterTokenService reporterTokens;
    private final Clock clock;

    /**
     * Entra com um e-mail já confirmado, criando a conta no primeiro acesso.
     *
     * @param previousReporterId a identidade anônima que o navegador já usava: no primeiro acesso, ela vira a da
     *                           conta, e os relatos de antes aparecem em "Meus relatos"
     */
    @Transactional
    public ReporterTokenResponse signIn(String email, Optional<UUID> previousReporterId) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        CitizenAccount account = accounts.findByEmail(AppUser.normalizeEmail(email)).orElseGet(() -> {
            UUID reporterId = previousReporterId.filter(id -> !accounts.existsByReporterId(id))
                    .orElseGet(UUID::randomUUID);
            return accounts.save(CitizenAccount.of(email, reporterId, now));
        });
        if (reporterTokens.isBlocked(account.getReporterId())) {
            throw new ReporterBlockedException();
        }
        account.loggedIn(now);
        log.info("Cidadão {} entrou", account.getId());
        return reporterTokens.issueFor(account.getReporterId());
    }

    /** Relatar exige o e-mail confirmado: um token de identidade anônima, de antes do login, não basta. */
    @Transactional(readOnly = true)
    public void requireAccount(UUID reporterId) {
        if (!accounts.existsByReporterId(reporterId)) {
            throw new CitizenLoginRequiredException();
        }
    }
}
