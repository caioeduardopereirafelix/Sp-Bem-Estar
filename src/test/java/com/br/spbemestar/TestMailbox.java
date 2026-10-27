package com.br.spbemestar;

import com.br.spbemestar.service.mail.EmailSender;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Caixa de entrada dos testes: guarda o último e-mail de cada destinatário, no lugar do Brevo. */
public class TestMailbox implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final Map<String, Email> inbox = new ConcurrentHashMap<>();
    private volatile EmailSender failure;

    @Override
    public void send(Email email) {
        if (failure != null) {
            failure.send(email);
        }
        inbox.put(email.to(), email);
    }

    /** Até ser chamado com {@code null}, os envios passam por esse remetente, para simular o Brevo fora do ar. */
    public void failWith(EmailSender sender) {
        this.failure = sender;
    }

    public Optional<Email> last(String to) {
        return Optional.ofNullable(inbox.get(to));
    }

    /** O código de 6 números do último e-mail para esse endereço. */
    public String code(String to) {
        Email email = last(to).orElseThrow(() -> new AssertionError("nenhum e-mail para " + to));
        Matcher matcher = CODE.matcher(email.text());
        if (!matcher.find()) {
            throw new AssertionError("e-mail sem código: " + email.text());
        }
        return matcher.group(1);
    }
}
