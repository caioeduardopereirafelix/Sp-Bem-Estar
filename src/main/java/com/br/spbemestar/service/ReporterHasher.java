package com.br.spbemestar.service;

import com.br.spbemestar.config.ReporterProperties;
import com.br.spbemestar.util.Crypto;
import org.springframework.stereotype.Component;

import java.util.UUID;
import javax.crypto.SecretKey;

@Component
public class ReporterHasher {

    private final SecretKey key;

    public ReporterHasher(ReporterProperties properties) {
        this.key = Crypto.hmacKey(properties.hashSecret(), "app.reporter.hash-secret (REPORTER_HASH)");
    }

    public String reporterHash(UUID reporterId) {
        return Crypto.hmacHex(key, reporterId.toString());
    }

    public static String tokenHash(String token) {
        return Crypto.sha256Hex(token);
    }
}
