package com.br.spbemestar.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateOperatorRequest(@NotNull Boolean active) {
}
