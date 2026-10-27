package com.br.spbemestar.service.mail;

/** Sem Brevo e sem o log de desenvolvimento: ninguém recebe código, e o pedido avisa que o envio está desligado. */
public class DisabledEmailSender implements EmailSender {

    @Override
    public void send(Email email) {
        throw new EmailDeliveryException("Envio de e-mail não configurado (BREVO_API_KEY e MAIL_FROM)");
    }
}
