package cl.duoc.cloudnative.notification.messaging;

import cl.duoc.cloudnative.notification.model.Notification;
import cl.duoc.cloudnative.notification.service.NotificationService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductEventConsumerTests {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Channel channel;

    @Test
    void productCreatedEventCreatesNotificationAndAcknowledgesMessage() throws Exception {
        ProductEventConsumer consumer = new ProductEventConsumer(notificationService);
        ProductEvent event = new ProductEvent(
                UUID.randomUUID(),
                "PRODUCT_CREATED",
                25L,
                "Monitor",
                OffsetDateTime.now()
        );

        consumer.consume(event, message(42L), channel);

        ArgumentCaptor<Notification> notification = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).create(notification.capture());
        verify(channel).basicAck(42L, false);
        assertThat(notification.getValue().getRecipient()).isEqualTo("inventory");
        assertThat(notification.getValue().getMessage()).contains("Producto creado", "Monitor", "25");
    }

    @Test
    void unsupportedEventIsRejectedWithoutRequeue() throws Exception {
        ProductEventConsumer consumer = new ProductEventConsumer(notificationService);
        ProductEvent event = new ProductEvent(
                UUID.randomUUID(),
                "UNKNOWN_EVENT",
                26L,
                "Evento invalido",
                OffsetDateTime.now()
        );

        consumer.consume(event, message(43L), channel);

        verify(channel).basicNack(43L, false, false);
    }

    private Message message(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
