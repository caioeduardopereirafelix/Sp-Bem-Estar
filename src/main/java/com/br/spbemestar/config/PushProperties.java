package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * Notificações push para o cidadão (Web Push com VAPID). Sem as chaves, o recurso fica desligado e o
 * mapa nem oferece a opção.
 *
 * @param vapidPublicKey       chave pública P-256 (ponto não comprimido, base64url); o navegador a usa na inscrição
 * @param vapidPrivateKey      chave privada (32 bytes, base64url); gere o par com {@code node scripts/generate-vapid-keys.mjs}
 * @param subject              contato do responsável ({@code mailto:} ou {@code https:}), exigido pelos serviços de push
 * @param allowedHosts         serviços de push aceitos como endpoint; {@code *.} no começo vale para subdomínios.
 *                             O endpoint vem do navegador: sem essa lista, a API faria requisições para qualquer URL
 * @param maxSubscriptionsPerReporter navegadores por cidadão; o mais antigo sai quando passa disso
 * @param ttl                  por quanto tempo o serviço de push guarda o aviso se o aparelho estiver desligado
 * @param allowInsecureEndpoints aceita endpoints {@code http:}; só para os testes, com um servidor local
 */
@ConfigurationProperties("app.push")
public record PushProperties(
        String vapidPublicKey,
        String vapidPrivateKey,
        String subject,
        @DefaultValue({"fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com",
                "*.notify.windows.com"}) List<String> allowedHosts,
        @DefaultValue("5") int maxSubscriptionsPerReporter,
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("false") boolean allowInsecureEndpoints
) {

    public boolean enabled() {
        return StringUtils.hasText(vapidPublicKey) && StringUtils.hasText(vapidPrivateKey);
    }
}
