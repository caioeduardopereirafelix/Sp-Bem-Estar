package com.br.spbemestar.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Collection;

/**
 * Encoder e decoder dos tokens de cidadão. A chave é derivada do {@code JWT_SECRET}, mas é outra:
 * um token de cidadão nunca passa na verificação de assinatura das rotas de operador, e vice-versa,
 * mesmo que uma regra de autorização seja configurada errado.
 * <p>
 * Não são beans {@code JwtEncoder}/{@code JwtDecoder} para não concorrer com os do operador.
 */
@Component
public class ReporterJwt {

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public ReporterJwt(SecurityProperties security, ReporterProperties reporter) {
        SecretKey key = deriveKey(SecurityConfig.secretKey(security));
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(security.issuer()),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD,
                        aud -> aud != null && aud.contains(reporter.audience()))));
        this.decoder = decoder;
    }

    public JwtEncoder encoder() {
        return encoder;
    }

    public JwtDecoder decoder() {
        return decoder;
    }

    private static SecretKey deriveKey(SecretKey operatorKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(operatorKey);
            byte[] derived = mac.doFinal("spbemestar/reporter-token/v1".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(derived, "HmacSHA256");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
