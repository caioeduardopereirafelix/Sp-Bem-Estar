package com.br.spbemestar.service.mail;

public interface EmailSender {

    void send(Email email);

    record Email(String to, String subject, String text, String html) {
    }
}
