package cl.duoc.cloudnative.notification.messaging;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProductMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductMetricsConsumer.class);

    @RabbitListener(queues = "${app.messaging.product-metrics-queue}")
    public void consume(ProductEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            ProductEventPolicy.requireSupported(event);
            log.info("Metrica de producto procesada: type={}, productId={}, eventId={}",
                    event.eventType(), event.productId(), event.eventId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
            log.error("Evento de metricas rechazado y enviado a DLQ: eventId={}", event.eventId(), exception);
        }
    }
}
