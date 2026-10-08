package cl.duoc.cloudnative.notification.messaging;

import cl.duoc.cloudnative.notification.model.Notification;
import cl.duoc.cloudnative.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProductEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private final NotificationService notificationService;

    public ProductEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${app.messaging.product-events-queue}")
    public void consume(ProductEvent event, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Notification notification = new Notification();
            notification.setRecipient("inventory");
            notification.setMessage(messageFor(event));
            notificationService.create(notification);

            channel.basicAck(deliveryTag, false);
            log.info("Evento confirmado con ACK: type={}, productId={}, eventId={}",
                    event.eventType(), event.productId(), event.eventId());
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
            log.error("Evento rechazado con NACK y enviado a DLQ: type={}, productId={}, eventId={}",
                    event.eventType(), event.productId(), event.eventId(), exception);
        }
    }

    private String messageFor(ProductEvent event) {
        return switch (event.eventType()) {
            case "PRODUCT_CREATED" -> "Producto creado: " + event.productName() + " (ID " + event.productId() + ")";
            case "PRODUCT_UPDATED" -> "Producto actualizado: " + event.productName() + " (ID " + event.productId() + ")";
            case "PRODUCT_DELETED" -> "Producto eliminado: " + event.productName() + " (ID " + event.productId() + ")";
            default -> throw new IllegalArgumentException("Tipo de evento no soportado: " + event.eventType());
        };
    }
}
