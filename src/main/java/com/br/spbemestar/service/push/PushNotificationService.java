package com.br.spbemestar.service.push;

import com.br.spbemestar.config.PushProperties;
import com.br.spbemestar.dto.PushSubscriptionRequest;
import com.br.spbemestar.exception.InvalidPushSubscriptionException;
import com.br.spbemestar.exception.PushDisabledException;
import com.br.spbemestar.exception.ReporterBlockedException;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.PushSubscription;
import com.br.spbemestar.repository.BlockedReporterRepository;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.PushSubscriptionRepository;
import com.br.spbemestar.repository.ReportRepository;
import com.br.spbemestar.service.ReporterHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Inscrições de push do cidadão e os avisos de mudança de status dos chamados em que ele relatou. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository subscriptions;
    private final ReportRepository reports;
    private final IssueRepository issues;
    private final BlockedReporterRepository blockedReporters;
    private final ReporterHasher hasher;
    private final WebPushSender sender;
    private final PushProperties properties;
    private final JsonMapper json;
    private final Clock clock;

    @Transactional
    public void subscribe(UUID reporterId, PushSubscriptionRequest request) {
        if (!sender.enabled()) {
            throw new PushDisabledException();
        }
        String reporterHash = hasher.reporterHash(reporterId);
        if (blockedReporters.existsById(reporterHash)) {
            throw new ReporterBlockedException();
        }
        if (!sender.allowedEndpoint(request.endpoint())) {
            throw new InvalidPushSubscriptionException("O endpoint não é de um serviço de push conhecido.");
        }
        validateKeys(request.keys());

        Instant now = clock.instant();
        subscriptions.findByEndpoint(request.endpoint()).ifPresentOrElse(
                existing -> existing.update(reporterHash, request.keys().p256dh(), request.keys().auth(), now),
                () -> subscriptions.save(PushSubscription.of(reporterHash, request.endpoint(),
                        request.keys().p256dh(), request.keys().auth(), now)));
        subscriptions.flush();

        // cada navegador do cidadão é uma inscrição; acima do limite, sai a usada há mais tempo
        List<PushSubscription> mine = subscriptions.findByReporterHashOrderByUpdatedAtDesc(reporterHash);
        if (mine.size() > properties.maxSubscriptionsPerReporter()) {
            subscriptions.deleteAll(mine.subList(properties.maxSubscriptionsPerReporter(), mine.size()));
        }
    }

    @Transactional
    public void unsubscribe(UUID reporterId, String endpoint) {
        subscriptions.deleteByEndpointAndReporterHash(endpoint, hasher.reporterHash(reporterId));
    }

    /**
     * Avisa quem relatou o problema. Roda fora da transação que mudou o status (vem da outbox pelo RabbitMQ),
     * então um serviço de push lento não segura a requisição do operador.
     *
     * @return quantos navegadores receberam o aviso
     */
    public int notifyStatusChange(Long issueId, IssueStatus status) {
        if (!sender.enabled() || !notifiable(status)) {
            return 0;
        }
        String categoryName = issues.findCategoryNameById(issueId).orElse(null);
        List<String> reporters = categoryName == null ? List.of() : reports.findReportersToNotify(issueId);
        if (reporters.isEmpty()) {
            return 0;
        }
        byte[] payload = payload(issueId, categoryName, status);
        int delivered = 0;
        List<PushSubscription> expired = new ArrayList<>();
        for (PushSubscription subscription : subscriptions.findByReporterHashIn(reporters)) {
            switch (sender.send(subscription, payload)) {
                case DELIVERED -> delivered++;
                case EXPIRED -> expired.add(subscription);
                case FAILED -> { } // já registrado pelo sender; a inscrição continua valendo
            }
        }
        if (!expired.isEmpty()) {
            subscriptions.deleteAll(expired);
            log.info("{} inscrição(ões) de push vencida(s) removida(s)", expired.size());
        }
        log.info("Chamado {} ({}): aviso entregue a {} navegador(es)", issueId, status, delivered);
        return delivered;
    }

    public static boolean notifiable(IssueStatus status) {
        return status == IssueStatus.CONFIRMED || status == IssueStatus.IN_PROGRESS || status == IssueStatus.RESOLVED;
    }

    /**
     * O corpo vai cifrado até o navegador; o service worker do mapa monta a notificação com ele e, no clique,
     * abre {@code /?issue=<id>} do próprio site.
     */
    private byte[] payload(Long issueId, String categoryName, IssueStatus status) {
        String subject = categoryName + " #" + issueId;
        Map<String, Object> message = new LinkedHashMap<>();
        switch (status) {
            case CONFIRMED -> {
                message.put("title", "Problema confirmado");
                message.put("body", subject + ": a prefeitura confirmou o problema que você relatou.");
            }
            case IN_PROGRESS -> {
                message.put("title", "Atendimento em andamento");
                message.put("body", subject + ": a equipe começou a resolver o problema que você relatou.");
            }
            default -> {
                message.put("title", "Problema resolvido");
                message.put("body", subject + " foi marcado como resolvido. Obrigado por relatar!");
            }
        }
        message.put("issueId", issueId);
        message.put("status", status.name());
        message.put("tag", "issue-" + issueId); // um aviso por chamado: o novo substitui o anterior
        return json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8);
    }

    private static void validateKeys(PushSubscriptionRequest.Keys keys) {
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            WebPushEncryption.publicKey(b64.decode(keys.p256dh()));
            if (b64.decode(keys.auth()).length != 16) {
                throw new InvalidPushSubscriptionException("O segredo auth da inscrição precisa ter 16 bytes.");
            }
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new InvalidPushSubscriptionException("As chaves da inscrição não são válidas.");
        }
    }
}
