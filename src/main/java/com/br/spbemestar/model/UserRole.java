package com.br.spbemestar.model;

import java.util.List;

public enum UserRole {

    // Servidor do órgão público: confirma, encaminha e resolve chamados
    OPERATOR,
    // Também cadastra, desativa e reativa operadores
    ADMIN,
    // Dono do produto: só vê o painel de métricas e saúde. Não mexe em chamados e fica fora da equipe do órgão
    CEO;

    /** Papéis gravados no token: o administrador também é operador. */
    public List<String> grantedRoles() {
        return this == ADMIN ? List.of(ADMIN.name(), OPERATOR.name()) : List.of(name());
    }
}
