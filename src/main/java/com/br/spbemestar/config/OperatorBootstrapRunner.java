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

@Slf4j
@Component
@RequiredArgsConstructor
public class OperatorBootstrapRunner implements ApplicationRunner {

    private final SecurityProperties properties;
    private final AppUserRepository users;
    private final AuthService authService;

    @Override
    public void run(ApplicationArguments args) {
        create(properties.bootstrapOperator(), UserRole.ADMIN, "Administrador inicial");
        create(properties.bootstrapCeo(), UserRole.CEO, "Conta do CEO");
    }

    private void create(SecurityProperties.BootstrapOperator account, UserRole role, String description) {
        if (account == null || !account.enabled()) {
            return;
        }
        String email = AppUser.normalizeEmail(account.email());
        if (users.existsByEmail(email)) {
            return;
        }
        AppUser user = authService.createUser(email, account.name(), role);
        log.info("{} criada (usuário {})", description, user.getId());
    }
}
