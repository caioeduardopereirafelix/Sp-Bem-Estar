package com.br.spbemestar.util;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Crypto {

    public static final String HMAC_SHA256 = "HmacSHA256";

    private Crypto() {
    }

    public static SecretKey hmacKey(String secret, String setting) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(setting + " precisa ter ao menos 32 bytes");
        }
        return new SecretKeySpec(bytes, HMAC_SHA256);
    }

    public static SecretKey deriveKey(SecretKey base, String label) {
        return new SecretKeySpec(hmac(base.getEncoded(), utf8(label)), HMAC_SHA256);
    }

    public static String hmacHex(SecretKey key, String text) {
        return HexFormat.of().formatHex(hmac(key.getEncoded(), utf8(text)));
    }

    public static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(key, HMAC_SHA256));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }

    public static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(utf8(text)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
