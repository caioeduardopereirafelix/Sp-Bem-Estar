package com.br.spbemestar.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;

/**
 * Criptografia do corpo de uma notificação Web Push (RFC 8291, {@code aes128gcm} da RFC 8188), só com o JDK.
 * O serviço de push (Google, Mozilla, Apple) apenas repassa a mensagem: quem decifra é o navegador, com as
 * chaves que ele mesmo gerou na inscrição ({@code p256dh} e {@code auth}).
 */
public final class WebPushEncryption {

    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = p256();

    private WebPushEncryption() {
    }

    /**
     * @param uaPublic chave pública do navegador (p256dh), ponto não comprimido de 65 bytes
     * @param authSecret segredo de 16 bytes do navegador (auth)
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, salt, newKeyPair());
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, byte[] salt, KeyPair asKeys) {
        if (plaintext.length > RECORD_SIZE - 16 - 1 - 86) {
            throw new IllegalArgumentException("Notificação grande demais para um registro");
        }
        try {
            byte[] asPublic = encodePoint((ECPublicKey) asKeys.getPublic());
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(asKeys.getPrivate());
            agreement.doPhase(publicKey(uaPublic), true);
            byte[] ecdhSecret = agreement.generateSecret();

            // RFC 8291, seção 3.4: o segredo do navegador entra na derivação, junto com as duas chaves públicas
            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2})); // 0x02: último registro

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length)
                    .put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar a notificação", e);
        }
    }

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponível", e);
        }
    }

    /** Ponto não comprimido (0x04 || x || y), o formato que o navegador entrega em p256dh. */
    public static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("Chave pública P-256 inválida");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    public static ECPrivateKey privateKey(byte[] d) throws GeneralSecurityException {
        if (d.length != 32) {
            throw new GeneralSecurityException("Chave privada P-256 inválida");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC")
                .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }

    public static byte[] encodePoint(ECPublicKey key) {
        return concat(new byte[]{4}, unsigned32(key.getW().getAffineX()), unsigned32(key.getW().getAffineY()));
    }

    /** HKDF-SHA256 (RFC 5869) para saídas de até 32 bytes: um único bloco de expansão. */
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        return Arrays.copyOf(hmac(prk, concat(info, new byte[]{1})), length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("P-256 indisponível", e);
        }
    }
}
