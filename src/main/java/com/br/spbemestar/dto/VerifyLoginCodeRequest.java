package com.br.spbemestar.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyLoginCodeRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "o código tem 6 números") String code,
        @Size(max = 4096) String reporterToken
) {
}
