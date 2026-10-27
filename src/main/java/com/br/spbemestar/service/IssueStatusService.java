package com.br.spbemestar.service;

import com.br.spbemestar.config.TraceContext;
import com.br.spbemestar.dto.StatusChangeResponse;
import com.br.spbemestar.exception.InvalidResolutionException;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

@Service
@RequiredArgsConstructor
public class IssueStatusService {

    public static final String ISSUE_STATUS_CHANGED = "IssueStatusChanged";

    private final IssueRepository issues;
    private final IssueStatusHistoryRepository history;
    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final TraceContext traceContext;
    private final MeterRegistry meters;
    private final ExifStripper exifStripper;
    private final PhotoStorage photoStorage;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /** Nota da resolução: curta, para caber na notificação e no pop-up do mapa. */
    public static final int MAX_RESOLUTION_NOTE = 500;

    @Transactional
    public StatusChangeResponse change(Long issueId, IssueStatus target, Long operatorId) {
        return apply(issueId, operatorId, (issue, now) -> issue.changeStatus(target, now, operatorId));
    }

    /**
     * Resolve o chamado com a foto do conserto e uma nota, os dois opcionais. A foto passa pelo mesmo tratamento
     * das fotos dos relatos (EXIF fora, formato conferido) e é gravada antes da transação; se a mudança de status
     * falhar, ela é apagada.
     */
    public StatusChangeResponse resolve(Long issueId, Long operatorId, byte[] photo, String note) {
        String cleanNote = cleanNote(note);
        String photoId = null;
        if (photo != null && photo.length > 0) {
            ExifStripper.SanitizedPhoto sanitized = exifStripper.strip(photo);
            photoId = photoStorage.store(new ByteArrayInputStream(sanitized.content()), sanitized.contentType());
        }
        String storedPhotoId = photoId;
        try {
            return transaction.execute(status -> apply(issueId, operatorId,
                    (issue, now) -> issue.resolve(now, operatorId, storedPhotoId, cleanNote)));
        } catch (RuntimeException e) {
            if (storedPhotoId != null) {
                photoStorage.delete(storedPhotoId);
            }
            throw e;
        }
    }

    private static String cleanNote(String note) {
        if (note == null) {
            return null;
        }
        // quebras de linha viram espaço: a nota é uma frase, e caracteres de controle não têm o que fazer ali
        String clean = note.replaceAll("\\p{Cntrl}+", " ").strip();
        if (clean.length() > MAX_RESOLUTION_NOTE) {
            throw new InvalidResolutionException(
                    "A nota pode ter até %d caracteres (tem %d).".formatted(MAX_RESOLUTION_NOTE, clean.length()));
        }
        return clean.isEmpty() ? null : clean;
    }

    private StatusChangeResponse apply(Long issueId, Long operatorId,
                                       BiFunction<Issue, Instant, IssueStatusHistory> mutation) {
        Issue issue = issues.findById(issueId).orElseThrow(() -> new ResourceNotFoundException("Chamado", issueId));
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        IssueStatusHistory change = history.save(mutation.apply(issue, now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issueId", issueId);
        payload.put("from", change.getFromStatus().name());
        payload.put("to", change.getToStatus().name());
        payload.put("changedBy", operatorId);
        outbox.save(OutboxEvent.of(issueId, ISSUE_STATUS_CHANGED, json.writeValueAsString(payload), now,
                traceContext.currentTraceParent()));

        meters.counter("spbemestar.issues.status.changes", "to", change.getToStatus().name()).increment();
        return new StatusChangeResponse(issueId, change.getFromStatus().name(), change.getToStatus().name(),
                change.getChangedAt(), issue.getResolvedAt());
    }
}
