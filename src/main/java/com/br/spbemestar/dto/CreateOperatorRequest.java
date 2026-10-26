package com.br.spbemestar.dto;

import com.br.spbemestar.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param password senha inicial, que o operador troca no primeiro acesso pela área do operador */
public record CreateOperatorRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
