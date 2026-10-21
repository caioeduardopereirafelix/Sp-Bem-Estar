package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * @param enabled           liga o limite; desligado, nenhuma requisição é contada
 * @param maxTrackedClients quantos pares (regra, IP) ficam em memória; os menos usados saem primeiro
 * @param rules             vale a primeira regra que casar com a requisição; o que não casa com nenhuma fica livre
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("100000") int maxTrackedClients,
        @DefaultValue List<Rule> rules
) {

    /**
     * @param name     identifica a regra nos logs e separa os contadores de cada uma
     * @param method   método HTTP; vazio vale para todos
     * @param path     padrão de caminho, como nas regras do Spring Security ({@code /api/**})
     * @param capacity requisições permitidas por IP a cada {@code period}
     * @param period   janela em que a capacidade é reposta, aos poucos
     */
    public record Rule(String name, String method, String path, int capacity, Duration period) {
    }
}
