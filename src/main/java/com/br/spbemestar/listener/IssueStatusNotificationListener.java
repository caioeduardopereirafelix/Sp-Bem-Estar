package com.br.spbemestar.listener;

import com.br.spbemestar.config.RabbitConfig;
import com.br.spbemestar.model.IssueStatus;
import com.br.spbemestar.service.push.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe da outbox cada mudança de status e avisa por push quem relatou o problema. Uma notificação que
 * o serviço de push recusa não volta para a fila: reenviar faria os outros navegadores receberem duas vezes.
 */
@Component
@RequiredArgsConstructor
public class IssueStatusNotificationListener {

    private final JsonMapper json;
    private final PushNotificationService notifications;

    @RabbitListener(queues = RabbitConfig.STATUS_NOTIFICATION_QUEUE)
    public void onIssueStatusChanged(Message message) {
        Long issueId;
        IssueStatus status;
        try {
            JsonNode event = json.readTree(message.getBody());
            issueId = event.path("issueId").asLong();
            status = IssueStatus.valueOf(event.path("to").asString());
        } catch (JacksonException | IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException("Mudança de status inválida", e);
        }
        if (issueId <= 0) {
            throw new AmqpRejectAndDontRequeueException("Mudança de status sem chamado");
        }
        notifications.notifyStatusChange(issueId, status);
    }
}
