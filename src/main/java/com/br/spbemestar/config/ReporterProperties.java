package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param hashSecret      chave do HMAC que transforma o id do cidadão no {@code reporter_hash} dos relatos;
 *                        fica fora do banco, então um vazamento do banco não liga relatos a tokens
 * @param accessTokenTtl  validade do JWT do cidadão; curta, porque um JWT não pode ser revogado
 * @param refreshTokenTtl validade do refresh token, renovada a cada uso
 * @param audience        {@code aud} dos tokens de cidadão, que não servem em rotas de operador
 */
@ConfigurationProperties("app.reporter")
public record ReporterProperties(
        String hashSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("90d") Duration refreshTokenTtl,
        @DefaultValue("spbemestar-reporter") String audience
) {
}
