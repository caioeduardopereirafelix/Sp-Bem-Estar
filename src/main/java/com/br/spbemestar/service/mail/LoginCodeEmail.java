package com.br.spbemestar.service.mail;

import java.time.Duration;

public final class LoginCodeEmail {

    private LoginCodeEmail() {
    }

    public static EmailSender.Email to(String email, String code, Duration ttl) {
        String text = """
                Seu código de acesso ao SP Bem-Estar é %s.

                Ele vale por %d minutos. Se não foi você que pediu, ignore este e-mail: sem o código, ninguém entra.
                """.formatted(code, ttl.toMinutes());
        String html = """
                <div style="font-family:system-ui,sans-serif;font-size:16px;color:#1b2130">
                  <p>Seu código de acesso ao <strong>SP Bem-Estar</strong> é</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:6px;margin:16px 0">%s</p>
                  <p>Ele vale por %d minutos. Se não foi você que pediu, ignore este e-mail: sem o código, ninguém entra.</p>
                </div>
                """.formatted(code, ttl.toMinutes());
        return new EmailSender.Email(email, code + " é o seu código do SP Bem-Estar", text, html);
    }
}
