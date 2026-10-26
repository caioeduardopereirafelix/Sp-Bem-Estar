package com.br.spbemestar.controller;

import com.br.spbemestar.config.PrivacyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dados da implantação que a página de privacidade mostra: quem é o responsável e como falar com ele. */
@RestController
@RequestMapping("/api/v1/privacy")
@RequiredArgsConstructor
@EnableConfigurationProperties(PrivacyProperties.class)
public class PrivacyController {

    public record PrivacyResponse(String controller, String contact) {
    }

    private final PrivacyProperties properties;

    @GetMapping
    public PrivacyResponse privacy() {
        return new PrivacyResponse(blankToNull(properties.controller()), blankToNull(properties.contact()));
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
