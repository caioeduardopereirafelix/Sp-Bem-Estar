package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Quem responde pelos dados na página de privacidade (LGPD, art. 9º e 41). Ficam fora do código porque
 * mudam a cada implantação.
 *
 * @param controller órgão ou entidade responsável pelo tratamento (controlador)
 * @param contact    e-mail ou página do encarregado (DPO), para pedidos sobre os dados
 */
@ConfigurationProperties("app.privacy")
public record PrivacyProperties(String controller, String contact) {
}
