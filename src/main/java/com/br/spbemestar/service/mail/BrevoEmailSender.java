package com.br.spbemestar.service.mail;

import com.br.spbemestar.config.MailProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** {@code POST /v3/smtp/email} da API transacional do Brevo. */
@Slf4j
public class BrevoEmailSender implements EmailSender {

    private final MailProperties properties;
    private final RestClient client;

    public BrevoEmailSender(MailProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public void send(Email email) {
        Request request = new Request(new Address(properties.fromName(), properties.from()),
                List.of(new Address(null, email.to())), email.subject(), email.text(), email.html());
        try {
            client.post()
                    .uri(properties.brevoUrl().replaceAll("/+$", "") + "/v3/smtp/email")
                    .header("api-key", properties.brevoApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // sem o destinatário no log: o e-mail é dado pessoal
            log.warn("O Brevo não aceitou o e-mail: {}", e.getMessage());
            throw new EmailDeliveryException("O Brevo não aceitou o e-mail", e);
        }
    }

    record Request(Address sender, List<Address> to, String subject, String textContent, String htmlContent) {
    }

    // sem "name": null no JSON: o destinatário vai só com o e-mail
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Address(String name, String email) {
    }
}
