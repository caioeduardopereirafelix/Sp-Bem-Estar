package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Conferência da foto na hora do envio, pelo worker de classificação ({@code POST /api/classify}).
 *
 * @param url              endereço do worker (ex.: {@code http://worker:8081}); vazio desliga a conferência, e os
 *                         relatos seguem só com a classificação pela fila
 * @param timeout          sem resposta nesse tempo, o relato é aceito e a classificação pela fila decide
 * @param rejectConfidence a partir dessa confiança num tipo diferente do escolhido, o envio é recusado
 */
@ConfigurationProperties("app.classification.precheck")
public record PhotoPrecheckProperties(
        String url,
        String username,
        String password,
        @DefaultValue("3s") Duration timeout,
        @DefaultValue("0.8") double rejectConfidence
) {

    public boolean enabled() {
        return url != null && !url.isBlank();
    }
}
