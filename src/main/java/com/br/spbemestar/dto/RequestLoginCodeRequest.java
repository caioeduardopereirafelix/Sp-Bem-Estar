package com.br.spbemestar.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestLoginCodeRequest(@NotBlank @Email @Size(max = 254) String email) {
}
