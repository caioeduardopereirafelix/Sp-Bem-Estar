package com.br.spbemestar.service.mail;

import com.br.spbemestar.config.MailProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrevoEmailSenderTest {

    // gerada a cada execução: nenhuma chave de verdade nos testes
    final String apiKey = "xkeysib-" + UUID.randomUUID();
    final AtomicReference<String> body = new AtomicReference<>();
    final AtomicReference<String> keyHeader = new AtomicReference<>();
    final AtomicReference<String> path = new AtomicReference<>();
    HttpServer server;

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void sendsTheTransactionalEmailToBrevo() throws IOException {
        BrevoEmailSender sender = senderAnswering(201);

        sender.send(new EmailSender.Email("pessoa@exemplo.com", "123456 é o seu código", "texto 123456", "<p>123456</p>"));

        assertThat(path.get()).isEqualTo("/v3/smtp/email");
        assertThat(keyHeader.get()).isEqualTo(apiKey);
        assertThat(body.get()).contains("\"sender\":{\"name\":\"SP Bem-Estar\",\"email\":\"remetente@exemplo.com\"}")
                .contains("\"email\":\"pessoa@exemplo.com\"")
                .contains("\"subject\":\"123456 é o seu código\"")
                .contains("\"textContent\":\"texto 123456\"")
                .contains("\"htmlContent\":\"<p>123456</p>\"");
    }

    @Test
    void aRefusalBecomesADeliveryError() throws IOException {
        BrevoEmailSender sender = senderAnswering(401);

        assertThatThrownBy(() -> sender.send(new EmailSender.Email("pessoa@exemplo.com", "s", "t", "h")))
                .isInstanceOf(EmailDeliveryException.class);
    }

    private BrevoEmailSender senderAnswering(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            keyHeader.set(exchange.getRequestHeaders().getFirst("api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = "{\"messageId\":\"<teste@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
        MailProperties properties = new MailProperties(apiKey, "http://127.0.0.1:" + server.getAddress().getPort(),
                "remetente@exemplo.com", "SP Bem-Estar", Duration.ofSeconds(2), false);
        return new BrevoEmailSender(properties, RestClient.create());
    }
}
