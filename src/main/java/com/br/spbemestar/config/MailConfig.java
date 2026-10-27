package com.br.spbemestar.config;

import com.br.spbemestar.service.mail.BrevoEmailSender;
import com.br.spbemestar.service.mail.DisabledEmailSender;
import com.br.spbemestar.service.mail.EmailSender;
import com.br.spbemestar.service.mail.LogEmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    EmailSender emailSender(MailProperties properties) {
        if (properties.brevoEnabled()) {
            HttpClient http = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
            JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
            requests.setReadTimeout(properties.timeout());
            return new BrevoEmailSender(properties, RestClient.builder().requestFactory(requests).build());
        }
        if (properties.logCodes()) {
            log.warn("E-mail sem Brevo: os códigos de acesso vão para o log (MAIL_LOG_CODES). Só para desenvolvimento");
            return new LogEmailSender();
        }
        log.warn("E-mail sem Brevo (BREVO_API_KEY e MAIL_FROM): ninguém consegue entrar até configurar");
        return new DisabledEmailSender();
    }
}
