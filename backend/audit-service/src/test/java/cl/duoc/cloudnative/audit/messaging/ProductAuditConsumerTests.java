package cl.duoc.cloudnative.audit.messaging;

import cl.duoc.cloudnative.audit.AuditService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ProductAuditConsumerTests {
    @Mock AuditService service;
    @Mock Channel channel;

    private ProductEvent event() {
        return new ProductEvent(UUID.randomUUID(), "PRODUCT_CREATED", 12L, "Monitor", OffsetDateTime.now());
    }
    private Message message() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(7L);
        return new Message(new byte[0], properties);
    }

    @Test
    void acknowledgesOnlyAfterPersistenceCompletes() throws Exception {
        ProductEvent event = event();
        new ProductAuditConsumer(service).consume(event, message(), channel);
        var order = inOrder(service, channel);
        order.verify(service).record(event);
        order.verify(channel).basicAck(7L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void persistenceFailureRejectsMessageToDlq() throws Exception {
        ProductEvent event = event();
        doThrow(new IllegalStateException("Database unavailable")).when(service).record(event);
        new ProductAuditConsumer(service).consume(event, message(), channel);
        verify(channel).basicNack(7L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void ackFailureDoesNotSendAnAdditionalNackAfterCommit() throws Exception {
        doThrow(new IOException("Connection closed")).when(channel).basicAck(7L, false);
        assertThatThrownBy(() -> new ProductAuditConsumer(service).consume(event(), message(), channel))
                .isInstanceOf(IOException.class);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }
}
