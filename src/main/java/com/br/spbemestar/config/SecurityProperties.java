package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param jwtSecret         chave HMAC dos tokens; no mínimo 32 bytes
 * @param tokenTtl          validade de um token
 * @param issuer            emissor gravado e conferido em cada token
 * @param bootstrapOperator operador criado na subida se ainda não existir (e-mail vazio = desligado)
 * @param bootstrapCeo      conta do CEO, criada do mesmo jeito; é ela que abre o painel em {@code /ceo}
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("8h") Duration tokenTtl,
        @DefaultValue("spbemestar-api") String issuer,
        @DefaultValue BootstrapOperator bootstrapOperator,
        @DefaultValue BootstrapOperator bootstrapCeo
) {

    /** Sem senha: a pessoa entra com o código que chega no e-mail. */
    public record BootstrapOperator(String email, @DefaultValue("Operador") String name) {

        public boolean enabled() {
            return email != null && !email.isBlank();
        }
    }
}
