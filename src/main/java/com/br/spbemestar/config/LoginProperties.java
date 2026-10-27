package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Login por código enviado por e-mail. Com os valores padrão, adivinhar um código de 6 números leva, em média,
 * milhares de horas: 5 códigos por hora para o mesmo e-mail, 5 tentativas cada, em 1 milhão de possibilidades.
 *
 * @param codeTtl         por quanto tempo um código vale
 * @param resendInterval  espera mínima entre dois códigos para o mesmo e-mail
 * @param maxCodesPerHour códigos para o mesmo e-mail por hora; cada um é um e-mail enviado
 * @param maxAttempts     tentativas erradas antes de o código deixar de valer
 */
@ConfigurationProperties("app.login")
public record LoginProperties(
        @DefaultValue("10m") Duration codeTtl,
        @DefaultValue("60s") Duration resendInterval,
        @DefaultValue("5") int maxCodesPerHour,
        @DefaultValue("5") int maxAttempts
) {
}
