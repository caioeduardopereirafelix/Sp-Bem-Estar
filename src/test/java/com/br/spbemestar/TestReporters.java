package com.br.spbemestar;

import com.br.spbemestar.service.CitizenAccountService;

import java.util.Optional;
import java.util.UUID;

public final class TestReporters {

    /** Cidadão usado nos testes que criam relatos direto pelo serviço. */
    public static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TestReporters() {
    }

    /** Header {@code Authorization} de um cidadão novo, já com o e-mail confirmado. */
    public static String bearer(CitizenAccountService citizens) {
        return "Bearer " + citizens.signIn(email(), Optional.empty()).accessToken();
    }

    /** Um e-mail que nenhum outro teste usa. */
    public static String email() {
        return "cidadao-" + UUID.randomUUID() + "@teste.com.br";
    }
}
