package com.br.spbemestar.service.push;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

/** Par VAPID novo a cada execução, no formato das variáveis PUSH_VAPID_*: nenhuma chave fica no repositório. */
public record TestVapidKeys(String publicKey, String privateKey) {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public static TestVapidKeys generate() {
        KeyPair pair = WebPushEncryption.newKeyPair();
        return new TestVapidKeys(B64.encodeToString(WebPushEncryption.encodePoint((ECPublicKey) pair.getPublic())),
                B64.encodeToString(unsigned32(((ECPrivateKey) pair.getPrivate()).getS())));
    }

    /** Chaves de inscrição de um navegador ({@code p256dh} e {@code auth}), também aleatórias. */
    public static String[] browserKeys() {
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        return new String[]{generate().publicKey(), B64.encodeToString(auth)};
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }
}
