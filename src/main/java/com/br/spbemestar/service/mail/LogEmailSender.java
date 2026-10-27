package com.br.spbemestar.service.mail;

import lombok.extern.slf4j.Slf4j;

/** Desenvolvimento sem Brevo: o e-mail vai para o log, para quem testa ler o código ali. */
@Slf4j
public class LogEmailSender implements EmailSender {

    @Override
    public void send(Email email) {
        log.warn("E-mail não enviado (sem BREVO_API_KEY, MAIL_LOG_CODES=true). Para {}: {}\n{}", email.to(),
                email.subject(), email.text());
    }
}
