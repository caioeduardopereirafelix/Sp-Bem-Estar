package com.br.spbemestar.service;

import com.br.spbemestar.config.ReporterProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * {@code reporter_hash} = HMAC-SHA256 do id do cidadão. A chave ({@code REPORTER_HASH}) não fica no banco:
 * quem tem só o banco vê que dois relatos são da mesma pessoa, mas não liga os relatos aos tokens.
 */
@Component
public class ReporterHasher {

    private final SecretKeySpec key;

    public ReporterHasher(ReporterProperties properties) {
        String secret = properties.hashSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.reporter.hash-secret (REPORTER_HASH) precisa ter ao menos 32 bytes");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String reporterHash(UUID reporterId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(reporterId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    /** O refresh token tem 256 bits aleatórios: um SHA-256 simples basta, sem hash lento como o de senha. */
    public static String tokenHash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
