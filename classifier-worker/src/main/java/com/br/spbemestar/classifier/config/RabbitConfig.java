package com.br.spbemestar.classifier.config;

import com.br.spbemestar.classifier.exception.RejectedReportException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mesma topologia declarada pela API ({@code com.br.spbemestar.config.RabbitConfig}). Declarar nos dois
 * lados deixa o worker e a API subirem em qualquer ordem sem perder mensagens; os argumentos precisam
 * ser idênticos, senão o RabbitMQ recusa a declaração.
 */
@Configuration
@EnableConfigurationProperties(ClassifierProperties.class)
public class RabbitConfig {

    public static final String EVENTS_EXCHANGE = "spbemestar.events";
    public static final String DEAD_LETTER_EXCHANGE = "spbemestar.events.dlx";

    public static final String REPORT_CREATED = "ReportCreated";
    public static final String REPORT_CLASSIFIED = "ReportClassified";

    public static final String CLASSIFICATION_QUEUE = "report.classification";
    public static final String CLASSIFICATION_DLQ = "report.classification.dlq";
    public static final String RESULT_QUEUE = "report.classification.result";
    public static final String RESULT_DLQ = "report.classification.result.dlq";

    /**
     * Roda uma vez por mensagem, quando o listener desiste (direto, ou depois das novas tentativas) e a
     * mensagem vai para a DLQ. Contar aqui, e não no listener, evita contar cada tentativa.
     */
    @Bean
    MessageRecoverer deadLetterRecoverer(MeterRegistry meters) {
        RejectAndDontRequeueRecoverer reject = new RejectAndDontRequeueRecoverer();
        return (message, cause) -> {
            meters.counter("classifier.reports", "outcome", reasonOf(cause), "label", "none").increment();
            reject.recover(message, cause);
        };
    }

    private static String reasonOf(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof RejectedReportException rejected) {
                return rejected.reason();
            }
        }
        return "error"; // falha transitória que esgotou as tentativas
    }

    @Bean
    TopicExchange eventsExchange() {
        return ExchangeBuilder.topicExchange(EVENTS_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

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

    @Bean
    Binding classificationBinding() {
        return BindingBuilder.bind(classificationQueue()).to(eventsExchange()).with(REPORT_CREATED);
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
