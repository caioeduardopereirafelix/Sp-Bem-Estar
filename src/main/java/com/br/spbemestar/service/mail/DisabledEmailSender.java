package com.br.spbemestar.service.mail;

public class DisabledEmailSender implements EmailSender {

    @Override
    public void send(Email email) {
        throw new EmailDeliveryException("Envio de e-mail não configurado (BREVO_API_KEY e MAIL_FROM)");
    }
}
