package cl.duoc.cloudnative.notification.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductSecondaryConsumersTests {

    @Mock
    private Channel channel;

    @Test
    void auditConsumerAcknowledgesSupportedEvent() throws Exception {
        new ProductAuditConsumer().consume(event("PRODUCT_CREATED"), message(51L), channel);

        verify(channel).basicAck(51L, false);
    }

    @Test
    void auditConsumerRejectsUnsupportedEventWithoutRequeue() throws Exception {
        new ProductAuditConsumer().consume(event("UNKNOWN_EVENT"), message(52L), channel);

        verify(channel).basicNack(52L, false, false);
    }

    @Test
    void metricsConsumerAcknowledgesSupportedEvent() throws Exception {
        new ProductMetricsConsumer().consume(event("PRODUCT_UPDATED"), message(61L), channel);

        verify(channel).basicAck(61L, false);
    }

    @Test
    void metricsConsumerRejectsUnsupportedEventWithoutRequeue() throws Exception {
        new ProductMetricsConsumer().consume(event("UNKNOWN_EVENT"), message(62L), channel);

        verify(channel).basicNack(62L, false, false);
    }

    private ProductEvent event(String type) {
        return new ProductEvent(UUID.randomUUID(), type, 30L, "Producto", OffsetDateTime.now());
    }

    private Message message(long deliveryTag) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], properties);
    }
}
