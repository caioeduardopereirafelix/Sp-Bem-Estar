package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

import java.time.Duration;

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
