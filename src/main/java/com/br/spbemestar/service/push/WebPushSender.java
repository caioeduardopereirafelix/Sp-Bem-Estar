package com.br.spbemestar.service.push;

import com.br.spbemestar.config.PushProperties;
import com.br.spbemestar.model.PushSubscription;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;

/**
 * Entrega uma notificação a um navegador: cifra o corpo (RFC 8291), assina o pedido com a chave VAPID
 * (RFC 8292) e faz o POST no endpoint do serviço de push.
 */
@Slf4j
@Component
public class WebPushSender {

    public enum Result {
        DELIVERED,
        /** O navegador cancelou a inscrição ou ela venceu: o endpoint não volta a funcionar. */
        EXPIRED,
        FAILED
    }

    private static final Duration JWT_VALIDITY = Duration.ofHours(12); // a RFC 8292 aceita até 24 h

    private final PushProperties properties;
    private final Clock clock;
    private final MeterRegistry meters;
    private final HttpClient http;
    private final ECPrivateKey privateKey;
    private final String publicKey;

    public WebPushSender(PushProperties properties, Clock clock, MeterRegistry meters) {
        this.properties = properties;
        this.clock = clock;
        this.meters = meters;
        // sem seguir redirecionamentos: o endpoint conferido na lista é o único destino
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        if (properties.enabled()) {
            this.privateKey = loadKeys(properties);
            this.publicKey = properties.vapidPublicKey();
        } else {
            this.privateKey = null;
            this.publicKey = null;
        }
    }

    public boolean enabled() {
        return privateKey != null;
    }

    /** Chave que o navegador passa ao {@code pushManager.subscribe()}. */
    public String publicKey() {
        return publicKey;
    }

    /** Só serviços de push conhecidos: o endpoint vem do navegador e poderia apontar para a rede interna. */
    public boolean allowedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || uri.getRawUserInfo() != null) {
            return false;
        }
        boolean https = "https".equalsIgnoreCase(uri.getScheme()) && (uri.getPort() == -1 || uri.getPort() == 443);
        boolean insecureAllowed = properties.allowInsecureEndpoints() && "http".equalsIgnoreCase(uri.getScheme());
        if (!https && !insecureAllowed) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return properties.allowedHosts().stream().map(h -> h.toLowerCase(Locale.ROOT)).anyMatch(allowed ->
                allowed.startsWith("*.") ? normalized.endsWith(allowed.substring(1)) : normalized.equals(allowed));
    }

    public Result send(PushSubscription subscription, byte[] payload) {
        Result result = deliver(subscription, payload);
        meters.counter("spbemestar.push.sent", "result", result.name().toLowerCase(Locale.ROOT)).increment();
        return result;
    }

    private Result deliver(PushSubscription subscription, byte[] payload) {
        if (!enabled() || !allowedEndpoint(subscription.getEndpoint())) {
            return Result.FAILED;
        }
        URI endpoint = URI.create(subscription.getEndpoint());
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            byte[] body = WebPushEncryption.encrypt(payload, b64.decode(subscription.getP256dh()),
                    b64.decode(subscription.getAuth()));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(10))
                    .header("TTL", String.valueOf(properties.ttl().toSeconds()))
                    .header("Urgency", "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", "vapid t=" + vapidToken(endpoint) + ", k=" + publicKey)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status >= 200 && status < 300) {
                return Result.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Result.EXPIRED;
            }
            // o endpoint identifica o navegador: no log, só o serviço de push
            log.warn("Serviço de push {} recusou a notificação da inscrição {}: HTTP {}", endpoint.getHost(),
                    subscription.getId(), status);
            return Result.FAILED;
        } catch (IOException | IllegalArgumentException | JOSEException e) {
            log.warn("Falha ao enviar push para {} (inscrição {}): {}", endpoint.getHost(), subscription.getId(),
                    e.toString());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    /** JWT da RFC 8292: o {@code aud} é a origem do serviço de push, e o {@code sub}, o contato do responsável. */
    private String vapidToken(URI endpoint) throws JOSEException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(audience)
                .expirationTime(Date.from(clock.instant().plus(JWT_VALIDITY)))
                .subject(properties.subject())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new ECDSASigner(privateKey));
        return jwt.serialize();
    }

    /** Falha na subida se as chaves não forem um par P-256 ou faltar o contato: melhor que notificações perdidas. */
    private static ECPrivateKey loadKeys(PushProperties properties) {
        String subject = properties.subject();
        if (!StringUtils.hasText(subject) || !(subject.startsWith("mailto:") || subject.startsWith("https://"))) {
            throw new IllegalStateException("app.push.subject (PUSH_VAPID_SUBJECT) precisa ser mailto: ou https://");
        }
        try {
            Base64.Decoder b64 = Base64.getUrlDecoder();
            ECPrivateKey privateKey = WebPushEncryption.privateKey(b64.decode(properties.vapidPrivateKey()));
            ECPublicKey publicKey = WebPushEncryption.publicKey(b64.decode(properties.vapidPublicKey()));
            SignedJWT probe = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), new JWTClaimsSet.Builder().build());
            probe.sign(new ECDSASigner(privateKey));
            if (!probe.verify(new ECDSAVerifier(publicKey))) {
                throw new IllegalStateException("PUSH_VAPID_PUBLIC_KEY não corresponde a PUSH_VAPID_PRIVATE_KEY");
            }
            return privateKey;
        } catch (GeneralSecurityException | IllegalArgumentException | JOSEException e) {
            throw new IllegalStateException("Chaves VAPID inválidas (PUSH_VAPID_PUBLIC_KEY e PUSH_VAPID_PRIVATE_KEY): "
                    + e.getMessage(), e);
        }
    }
}
