package com.br.spbemestar.dto;

import com.br.spbemestar.model.AppUser;

import java.time.Instant;

public record OperatorItem(Long id, String email, String name, String role, boolean active, Instant createdAt) {

    public static OperatorItem from(AppUser user) {
        return new OperatorItem(user.getId(), user.getEmail(), user.getName(), user.getRole().name(), user.isActive(),
                user.getCreatedAt());
    }
}
