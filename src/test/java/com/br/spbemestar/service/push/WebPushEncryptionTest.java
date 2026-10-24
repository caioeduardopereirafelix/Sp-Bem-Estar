package com.br.spbemestar.service.push;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WebPushEncryptionTest {

    private static final Base64.Decoder B64 = Base64.getUrlDecoder();

    /** Exemplo do apêndice A da RFC 8291, com o sal e as chaves do servidor fixos. */
    @Test
    void matchesTheRfc8291Example() throws Exception {
        byte[] plaintext = "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
        KeyPair asKeys = new KeyPair(
                WebPushEncryption.publicKey(B64.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8")),
                WebPushEncryption.privateKey(B64.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw")));

        byte[] body = WebPushEncryption.encrypt(plaintext,
                B64.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"),
                B64.decode("BTBZMqHH6r4Tts7J_aSIgg"),
                B64.decode("DGv6ra1nlYgDCS1FRnbzlw"),
                asKeys);

        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(body)).isEqualTo(
                "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");
    }
}
