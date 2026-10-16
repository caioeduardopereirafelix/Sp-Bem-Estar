package com.br.spbemestar.service;

import com.br.spbemestar.dto.StatusChangeResponse;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.Issue;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.model.IssueStatusHistory;
import com.br.spbemestar.model.OutboxEvent;
import com.br.spbemestar.repository.IssueRepository;
import com.br.spbemestar.repository.IssueStatusHistoryRepository;
import com.br.spbemestar.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IssueStatusService {

    public static final String ISSUE_STATUS_CHANGED = "IssueStatusChanged";

    private final IssueRepository issues;
    private final IssueStatusHistoryRepository history;
    private final OutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    @Transactional
    public StatusChangeResponse change(Long issueId, IssueStatus target) {
        Issue issue = issues.findById(issueId).orElseThrow(() -> new ResourceNotFoundException("Chamado", issueId));
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        IssueStatusHistory change = history.save(issue.changeStatus(target, now));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("issueId", issueId);
        payload.put("from", change.getFromStatus().name());
        payload.put("to", change.getToStatus().name());
        outbox.save(OutboxEvent.of(issueId, ISSUE_STATUS_CHANGED, json.writeValueAsString(payload), now));

        return new StatusChangeResponse(issueId, change.getFromStatus().name(), change.getToStatus().name(),
                change.getChangedAt(), issue.getResolvedAt());
    }
}
