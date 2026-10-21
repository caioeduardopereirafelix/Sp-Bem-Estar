package com.br.spbemestar;

import com.br.spbemestar.service.ReporterTokenService;

import java.util.UUID;

public final class TestReporters {

    /** Cidadão usado nos testes que criam relatos direto pelo serviço. */
    public static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private TestReporters() {
    }

    /** Header {@code Authorization} de um cidadão novo. */
    public static String bearer(ReporterTokenService tokens) {
        return "Bearer " + tokens.register().accessToken();
    }
}
