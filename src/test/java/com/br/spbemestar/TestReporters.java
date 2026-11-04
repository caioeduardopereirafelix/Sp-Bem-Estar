package com.br.spbemestar;

import com.br.spbemestar.service.CitizenAccountService;

import java.util.Optional;
import java.util.UUID;

public final class TestReporters {

    public static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TestReporters() {
    }

    public static String bearer(CitizenAccountService citizens) {
        return "Bearer " + citizens.signIn(email(), Optional.empty()).accessToken();
    }

    public static String email() {
        return "cidadao-" + UUID.randomUUID() + "@teste.com.br";
    }
}
