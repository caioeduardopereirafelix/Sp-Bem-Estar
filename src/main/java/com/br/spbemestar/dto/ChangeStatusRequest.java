package com.br.spbemestar.dto;

import com.br.spbemestar.model.IssueStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull IssueStatus status) {
}
