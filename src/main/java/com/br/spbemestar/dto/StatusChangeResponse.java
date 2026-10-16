package com.br.spbemestar.dto;

import java.time.Instant;

public record StatusChangeResponse(Long issueId, String from, String to, Instant changedAt, Instant resolvedAt) {
}
