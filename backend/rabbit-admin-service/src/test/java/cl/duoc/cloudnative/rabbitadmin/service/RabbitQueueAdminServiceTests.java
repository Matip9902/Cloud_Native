package cl.duoc.cloudnative.rabbitadmin.service;

import cl.duoc.cloudnative.rabbitadmin.dto.CreateQueueRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitQueueAdminServiceTests {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Test
    void createsDurableQueue() {
        RabbitQueueAdminService service = new RabbitQueueAdminService(amqpAdmin);
        when(amqpAdmin.declareQueue(org.mockito.ArgumentMatchers.any(Queue.class)))
                .thenReturn("orders.queue");

        service.create(new CreateQueueRequest("orders.queue", true));

        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(queue.capture());
        assertThat(queue.getValue().getName()).isEqualTo("orders.queue");
        assertThat(queue.getValue().isDurable()).isTrue();
    }

    @Test
    void deletesExistingQueue() {
        RabbitQueueAdminService service = new RabbitQueueAdminService(amqpAdmin);
        when(amqpAdmin.getQueueProperties("orders.queue")).thenReturn(new Properties());

        service.delete("orders.queue");

        verify(amqpAdmin).deleteQueue("orders.queue");
    }
}
