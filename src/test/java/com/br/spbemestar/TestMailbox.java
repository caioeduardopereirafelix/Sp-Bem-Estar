package com.br.spbemestar;

import com.br.spbemestar.service.mail.EmailSender;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public void failWith(EmailSender sender) {
        this.failure = sender;
    }

    public Optional<Email> last(String to) {
        return Optional.ofNullable(inbox.get(to));
    }

    public String code(String to) {
        Email email = last(to).orElseThrow(() -> new AssertionError("nenhum e-mail para " + to));
        Matcher matcher = CODE.matcher(email.text());
        if (!matcher.find()) {
            throw new AssertionError("e-mail sem código: " + email.text());
        }
        return matcher.group(1);
    }
}
