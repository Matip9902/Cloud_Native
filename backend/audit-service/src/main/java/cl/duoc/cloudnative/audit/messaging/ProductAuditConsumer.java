package cl.duoc.cloudnative.audit.messaging;

import cl.duoc.cloudnative.audit.AuditService;
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
    private final AuditService service;

    public ProductAuditConsumer(AuditService service) {
        this.service = service;
    }

    @RabbitListener(queues = "${app.messaging.product-audit-queue}")
    public void consume(ProductEvent event, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            // The service transaction commits before we acknowledge the RabbitMQ delivery.
            service.record(event);
        } catch (Exception exception) {
            log.error("Auditoria rechazada; envio a DLQ: eventId={}", event == null ? null : event.eventId(), exception);
            channel.basicNack(tag, false, false);
            return;
        }
        channel.basicAck(tag, false);
        log.info("Auditoria persistida y confirmada: eventId={}, type={}, productId={}",
                event.eventId(), event.eventType(), event.productId());
    }
}
