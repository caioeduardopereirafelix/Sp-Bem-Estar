package com.br.spbemestar;

import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import com.br.spbemestar.service.AuthService;

public final class TestOperators {

    public static final String EMAIL = "operador@teste.sp.gov.br";

    private TestOperators() {
    }

    /** Header {@code Authorization} de um operador ativo, criado na primeira chamada. */
    public static String bearer(AuthService auth, AppUserRepository users) {
        AppUser operator = users.findByEmail(EMAIL)
                .orElseGet(() -> auth.createUser(EMAIL, "Operador de teste", UserRole.OPERATOR));
        return "Bearer " + auth.issueToken(operator).accessToken();
    }
}
