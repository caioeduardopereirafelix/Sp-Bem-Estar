package com.br.spbemestar.model;

import java.util.List;

public enum UserRole {

    OPERATOR,
    ADMIN,
    CEO;

    public List<String> grantedRoles() {
        return this == ADMIN ? List.of(ADMIN.name(), OPERATOR.name()) : List.of(name());
    }
}
