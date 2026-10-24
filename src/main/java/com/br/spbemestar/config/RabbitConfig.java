package com.br.spbemestar.config;

import com.br.spbemestar.service.IssueStatusService;
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
 * mensagens rejeitadas. O classifier-worker declara a mesma topologia, com os mesmos argumentos,
 * para os dois poderem subir em qualquer ordem.
 */
@Configuration
@EnableConfigurationProperties({OutboxProperties.class, ClassificationProperties.class})
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "spbemestar.events";
    public static final String DEAD_LETTER_EXCHANGE = "spbemestar.events.dlx";

    public static final String REPORT_CLASSIFIED = "ReportClassified";

    public static final String CLASSIFICATION_QUEUE = "report.classification";
    public static final String CLASSIFICATION_DLQ = "report.classification.dlq";
    public static final String RESULT_QUEUE = "report.classification.result";
    public static final String RESULT_DLQ = "report.classification.result.dlq";
    public static final String STATUS_NOTIFICATION_QUEUE = "issue.status.notification";
    public static final String STATUS_NOTIFICATION_DLQ = "issue.status.notification.dlq";

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

    // Resultados do worker de classificação, consumidos pela própria API
    @Bean
    Queue resultQueue() {
        return QueueBuilder.durable(RESULT_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(RESULT_DLQ)
                .build();
    }

    @Bean
    Queue resultDeadLetterQueue() {
        return QueueBuilder.durable(RESULT_DLQ).build();
    }

    // Mudanças de status dos chamados, para avisar por push quem relatou
    @Bean
    Queue statusNotificationQueue() {
        return QueueBuilder.durable(STATUS_NOTIFICATION_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(STATUS_NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    Queue statusNotificationDeadLetterQueue() {
        return QueueBuilder.durable(STATUS_NOTIFICATION_DLQ).build();
    }

    @Bean
    Binding statusNotificationBinding() {
        return BindingBuilder.bind(statusNotificationQueue()).to(eventsExchange())
                .with(IssueStatusService.ISSUE_STATUS_CHANGED);
    }

    @Bean
    Binding statusNotificationDeadLetterBinding() {
        return BindingBuilder.bind(statusNotificationDeadLetterQueue()).to(deadLetterExchange())
                .with(STATUS_NOTIFICATION_DLQ);
    }

    @Bean
    Binding classificationBinding() {
        return BindingBuilder.bind(classificationQueue()).to(eventsExchange()).with(ReportService.REPORT_CREATED);
    }

    @Bean
    Binding classificationDeadLetterBinding() {
        return BindingBuilder.bind(classificationDeadLetterQueue()).to(deadLetterExchange()).with(CLASSIFICATION_DLQ);
    }

    @Bean
    Binding resultBinding() {
        return BindingBuilder.bind(resultQueue()).to(eventsExchange()).with(REPORT_CLASSIFIED);
    }

    @Bean
    Binding resultDeadLetterBinding() {
        return BindingBuilder.bind(resultDeadLetterQueue()).to(deadLetterExchange()).with(RESULT_DLQ);
    }
}
