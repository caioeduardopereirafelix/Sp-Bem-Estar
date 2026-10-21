package com.br.spbemestar.config;

import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro operador a partir de {@code app.security.bootstrap-operator}, para o órgão público
 * conseguir entrar logo após a instalação. Nunca altera um usuário que já existe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorBootstrapRunner implements ApplicationRunner {

    private final SecurityProperties properties;
    private final AppUserRepository users;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapOperator operator = properties.bootstrapOperator();
        if (operator == null || !operator.enabled()) {
            return;
        }
        String email = AppUser.normalizeEmail(operator.email());
        if (users.existsByEmail(email)) {
            return;
        }
        authService.createUser(email, operator.name(), operator.password(), UserRole.OPERATOR);
        log.info("Operador inicial {} criado", email);
    }
}
