package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Envio dos códigos de acesso por e-mail, pela API transacional do Brevo.
 *
 * @param brevoApiKey chave da API do Brevo (Configurações › Chaves SMTP e API); vazia, nenhum e-mail sai
 * @param brevoUrl    endereço da API; só os testes trocam
 * @param from        remetente, confirmado no Brevo (Remetentes); sem domínio próprio, pode ser o seu e-mail
 * @param fromName    nome que aparece como remetente
 * @param timeout     limite de cada envio: quem espera o código está com o app aberto
 * @param logCodes    sem chave do Brevo, escreve o código no log em vez de enviar. Só para desenvolvimento:
 *                    em produção, qualquer um com acesso ao log entraria na conta de qualquer pessoa
 */
@ConfigurationProperties("app.mail")
public record MailProperties(
        String brevoApiKey,
        @DefaultValue("https://api.brevo.com") String brevoUrl,
        String from,
        @DefaultValue("SP Bem-Estar") String fromName,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("false") boolean logCodes
) {

    public boolean brevoEnabled() {
        return StringUtils.hasText(brevoApiKey) && StringUtils.hasText(from);
    }
}
