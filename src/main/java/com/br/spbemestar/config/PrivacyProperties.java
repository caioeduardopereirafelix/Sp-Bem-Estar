package com.br.spbemestar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.privacy")
public record PrivacyProperties(String controller, String contact) {
}
