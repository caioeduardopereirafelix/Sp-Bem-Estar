package com.br.spbemestar.service.mail;

/** Envia um e-mail transacional (o código de acesso). */
public interface EmailSender {

    /**
     * @throws EmailDeliveryException se o e-mail não saiu: a pessoa pode pedir de novo
     */
    void send(Email email);

    record Email(String to, String subject, String text, String html) {
    }
}
