package cl.duoc.cloudnative.notification.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProductAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductAuditConsumer.class);

    @RabbitListener(queues = "${app.messaging.product-audit-queue}")
    public void consume(ProductEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ProductEventPolicy.requireSupported(event);
            log.info("Auditoria de producto: type={}, productId={}, eventId={}, occurredAt={}",
                    event.eventType(), event.productId(), event.eventId(), event.occurredAt());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
            log.error("Evento de auditoria rechazado y enviado a DLQ: eventId={}", event.eventId(), exception);
        }
    }
}
