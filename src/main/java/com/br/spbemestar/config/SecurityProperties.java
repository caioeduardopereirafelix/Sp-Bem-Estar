package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param jwtSecret         chave HMAC dos tokens; no mínimo 32 bytes
 * @param tokenTtl          validade de um token
 * @param issuer            emissor gravado e conferido em cada token
 * @param bootstrapOperator operador criado na subida se ainda não existir (e-mail e senha vazios = desligado)
 */
@ConfigurationProperties("app.security")
public record SecurityProperties(
        String jwtSecret,
        @DefaultValue("8h") Duration tokenTtl,
        @DefaultValue("spbemestar-api") String issuer,
        @DefaultValue BootstrapOperator bootstrapOperator
) {

    public record BootstrapOperator(String email, String password, @DefaultValue("Operador") String name) {

        public boolean enabled() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
