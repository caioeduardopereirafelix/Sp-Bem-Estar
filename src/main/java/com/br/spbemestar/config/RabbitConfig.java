package com.br.spbemestar.config;

import com.br.spbemestar.service.ReportService;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ. Os eventos da outbox saem pelo exchange {@value #EVENTS_EXCHANGE}
 * com o tipo do evento como routing key; cada consumidor tem sua fila e uma DLQ para as
 * mensagens rejeitadas.
 */
@Configuration
@EnableConfigurationProperties(OutboxProperties.class)
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "spbemestar.events";
    public static final String DEAD_LETTER_EXCHANGE = "spbemestar.events.dlx";

    public static final String CLASSIFICATION_QUEUE = "report.classification";
    public static final String CLASSIFICATION_DLQ = "report.classification.dlq";

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    // Relatos novos para o worker de classificação da foto
    @Bean
    Queue classificationQueue() {
        return QueueBuilder.durable(CLASSIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(CLASSIFICATION_DLQ)
                .build();
    }

    @Bean
    Queue classificationDeadLetterQueue() {
        return QueueBuilder.durable(CLASSIFICATION_DLQ).build();
    }

    @Bean
    Binding classificationBinding() {
        return BindingBuilder.bind(classificationQueue()).to(eventsExchange()).with(ReportService.REPORT_CREATED);
    }

    @Bean
    Binding classificationDeadLetterBinding() {
        return BindingBuilder.bind(classificationDeadLetterQueue()).to(deadLetterExchange()).with(CLASSIFICATION_DLQ);
    }
}
