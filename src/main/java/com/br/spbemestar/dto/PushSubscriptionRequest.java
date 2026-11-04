package com.br.spbemestar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1024) String endpoint,
        @NotNull @Valid Keys keys
) {

    public record Keys(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {
    }
}
