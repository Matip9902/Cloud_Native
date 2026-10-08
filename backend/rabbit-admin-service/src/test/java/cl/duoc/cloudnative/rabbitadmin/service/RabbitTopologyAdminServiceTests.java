package cl.duoc.cloudnative.rabbitadmin.service;

import cl.duoc.cloudnative.rabbitadmin.dto.BindingRequest;
import cl.duoc.cloudnative.rabbitadmin.dto.CreateExchangeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitTopologyAdminServiceTests {

    @Mock
    private AmqpAdmin amqpAdmin;

    @Test
    void createsTopicExchange() {
        RabbitExchangeAdminService service = new RabbitExchangeAdminService(amqpAdmin);

        service.create(new CreateExchangeRequest("orders.exchange", "topic", true));

        ArgumentCaptor<Exchange> exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(exchange.capture());
        assertThat(exchange.getValue().getName()).isEqualTo("orders.exchange");
        assertThat(exchange.getValue().getType()).isEqualTo("topic");
        assertThat(exchange.getValue().isDurable()).isTrue();
    }

    @Test
    void createsQueueBinding() {
        RabbitBindingAdminService service = new RabbitBindingAdminService(amqpAdmin);

        service.create(new BindingRequest("orders.exchange", "orders.queue", "orders.created"));

        ArgumentCaptor<Binding> binding = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(binding.capture());
        assertThat(binding.getValue().getExchange()).isEqualTo("orders.exchange");
        assertThat(binding.getValue().getDestination()).isEqualTo("orders.queue");
        assertThat(binding.getValue().getRoutingKey()).isEqualTo("orders.created");
    }
}
