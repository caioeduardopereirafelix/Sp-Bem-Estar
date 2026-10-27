package com.br.spbemestar.dto;

import com.br.spbemestar.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sem senha: a pessoa entra com o código que chega no e-mail cadastrado. */
public record CreateOperatorRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String name,
        @NotNull UserRole role
) {
}
