package com.br.spbemestar.config;

import com.br.spbemestar.repository.AppUserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Um JWT não se revoga sozinho: este validador confere, a cada requisição, se o usuário continua ativo e se
 * o token foi emitido depois da última desativação. É uma consulta por chave primária;
 * a equipe é pequena e a rota do operador não é a do mapa público.
 */
class OperatorTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token",
            "Token revogado: o usuário foi desativado.", null);

    // provider: o repositório depende do JPA, que sobe depois da configuração de segurança
    private final ObjectProvider<AppUserRepository> users;

    OperatorTokenValidator(ObjectProvider<AppUserRepository> users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Long userId;
        try {
            userId = Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException e) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        boolean valid = users.getObject().findById(userId)
                .map(user -> user.acceptsTokenIssuedAt(jwt.getIssuedAt()))
                .orElse(false);
        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(REVOKED);
    }
}
